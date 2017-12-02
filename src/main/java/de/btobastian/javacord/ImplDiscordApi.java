package de.btobastian.javacord;

import com.mashape.unirest.http.HttpMethod;
import de.btobastian.javacord.entities.Game;
import de.btobastian.javacord.entities.GameType;
import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.channels.GroupChannel;
import de.btobastian.javacord.entities.channels.TextChannel;
import de.btobastian.javacord.entities.impl.ImplGame;
import de.btobastian.javacord.entities.impl.ImplUser;
import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.entities.message.emoji.CustomEmoji;
import de.btobastian.javacord.entities.message.emoji.impl.ImplCustomEmoji;
import de.btobastian.javacord.entities.message.impl.ImplMessage;
import de.btobastian.javacord.listeners.connection.LostConnectionListener;
import de.btobastian.javacord.listeners.connection.ReconnectListener;
import de.btobastian.javacord.listeners.connection.ResumeListener;
import de.btobastian.javacord.listeners.message.MessageCreateListener;
import de.btobastian.javacord.listeners.message.MessageDeleteListener;
import de.btobastian.javacord.listeners.message.MessageEditListener;
import de.btobastian.javacord.listeners.message.reaction.ReactionAddListener;
import de.btobastian.javacord.listeners.message.reaction.ReactionRemoveListener;
import de.btobastian.javacord.listeners.server.*;
import de.btobastian.javacord.listeners.server.channel.*;
import de.btobastian.javacord.listeners.server.emoji.CustomEmojiCreateListener;
import de.btobastian.javacord.listeners.server.role.RoleChangePermissionsListener;
import de.btobastian.javacord.listeners.server.role.RoleChangePositionListener;
import de.btobastian.javacord.listeners.server.role.RoleCreateListener;
import de.btobastian.javacord.listeners.server.role.RoleDeleteListener;
import de.btobastian.javacord.listeners.server.role.UserRoleAddListener;
import de.btobastian.javacord.listeners.server.role.UserRoleRemoveListener;
import de.btobastian.javacord.listeners.user.UserChangeGameListener;
import de.btobastian.javacord.listeners.user.UserChangeNicknameListener;
import de.btobastian.javacord.listeners.user.UserChangeStatusListener;
import de.btobastian.javacord.listeners.user.UserStartTypingListener;
import de.btobastian.javacord.utils.DiscordWebsocketAdapter;
import de.btobastian.javacord.utils.ThreadPool;
import de.btobastian.javacord.utils.logging.LoggerUtil;
import de.btobastian.javacord.utils.ratelimits.RatelimitManager;
import de.btobastian.javacord.utils.rest.RestEndpoint;
import de.btobastian.javacord.utils.rest.RestRequest;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The implementation of {@link DiscordApi}.
 */
public class ImplDiscordApi implements DiscordApi {

    /**
     * The logger of this class.
     */
    private static final Logger logger = LoggerUtil.getLogger(ImplDiscordApi.class);

    /**
     * The thread pool which is used internally.
     */
    private final ThreadPool threadPool = new ThreadPool();

    /**
     * The ratelimit manager for this bot.
     */
    private final RatelimitManager ratelimitManager = new RatelimitManager(this);

    /**
     * The websocket adapter used to connect to Discord.
     */
    private DiscordWebsocketAdapter websocketAdapter = null;

    /**
     * The account type of the bot.
     */
    private final AccountType accountType;

    /**
     * The token used for authentication.
     */
    private String token;

    /**
     * The game which is currently displayed. May be <code>null</code>.
     */
    private Game game;

    /**
     * The default message cache capacity which is applied for every newly created channel.
     */
    private int defaultMessageCacheCapacity = 50;

    /**
     * The default maximum age of cached messages.
     */
    private int defaultMessageCacheStorageTimeInSeconds = 60*60*12;

    /**
     * The function to calculate the reconnect delay.
     */
    private Function<Integer, Integer> reconnectDelayProvider;

    /**
     * The current shard of the bot.
     */
    private final int currentShard;

    /**
     * The total amount of shards.
     */
    private final int totalShards;

    /**
     * The user of the connected account.
     */
    private User you;

    /**
     * The time offset between the Discord time and our local time.
     */
    private Long timeOffset = null;

    /**
     * A map which contains all users.
     */
    private final ConcurrentHashMap<Long, User> users = new ConcurrentHashMap<>();

    /**
     * A map which contains all servers.
     */
    private final ConcurrentHashMap<Long, Server> servers = new ConcurrentHashMap<>();

    /**
     * A map which contains all group channels.
     */
    private final ConcurrentHashMap<Long, GroupChannel> groupChannels = new ConcurrentHashMap<>();

    /**
     * A set with all unavailable servers.
     */
    private final HashSet<Long> unavailableServers = new HashSet<>();

    /**
     * A map with all known custom emoji.
     */
    private final ConcurrentHashMap<Long, CustomEmoji> customEmojis = new ConcurrentHashMap<>();

    /**
     * A map with all cached messages.
     */
    private final ConcurrentHashMap<Long, Message> messages = new ConcurrentHashMap<>();

    /**
     * A map which contains all listeners.
     * The key is the class of the listener.
     */
    private final ConcurrentHashMap<Class<?>, List<Object>> listeners = new ConcurrentHashMap<>();

    /**
     * A map which contains all listeners which are assigned to a specific object instead of being global.
     * The key of the outer map is the class which the listener was registered to (e.g. Message.class).
     * The key of the first inner map is the id of the object.
     * The key of the second inner map is the class of the listener.
     * The final value is the listener itself.
     */
    private final ConcurrentHashMap<Class<?>, Map<Long, Map<Class<?>, List<Object>>>> objectListeners =
            new ConcurrentHashMap<>();

    /**
     * Creates a new discord api instance.
     *
     * @param accountType The account type of the instance.
     * @param token The token used to connect without any account type specific prefix.
     * @param currentShard The current shard the bot should connect to.
     * @param totalShards  The total amount of shards.
     * @param ready The future which will be completed when the connection to Discord was successful.
     */
    public ImplDiscordApi(
            AccountType accountType,
            String token,
            int currentShard,
            int totalShards,
            CompletableFuture<DiscordApi> ready
    ){
        this.accountType = accountType;
        this.token = accountType.getTokenPrefix() + token;
        this.currentShard = currentShard;
        this.totalShards = totalShards;
        this.reconnectDelayProvider = x ->
                (int) Math.round(Math.pow(x, 1.5)-(1/(1/(0.1*x)+1))*Math.pow(x,1.5))*totalShards;

        RestEndpoint endpoint = RestEndpoint.GATEWAY_BOT;
        if (accountType == AccountType.CLIENT) {
            endpoint = RestEndpoint.GATEWAY;
        }

        new RestRequest<String>(this, HttpMethod.GET, endpoint)
                .includeAuthorizationHeader(accountType == AccountType.BOT)
                .execute(res -> res.getBody().getObject().getString("url"))
                .whenComplete((gateway, t) -> {
                    if (t != null) {
                        ready.completeExceptionally(t);
                        return;
                    }

                    websocketAdapter = new DiscordWebsocketAdapter(this, gateway);
                    websocketAdapter.isReady().whenComplete((readyReceived, throwable) -> {
                        if (readyReceived) {
                            ready.complete(this);
                        } else {
                            ready.completeExceptionally(
                                    new IllegalStateException("Websocket closed before READY packet was received!"));
                        }
                    });

                    getThreadPool().getScheduler().scheduleAtFixedRate(
                            () -> messages.entrySet().removeIf(entry -> !((ImplMessage) entry.getValue()).keepCached())
                            , 30, 30, TimeUnit.SECONDS);
                });
    }

    /**
     * Purges all cached entities.
     * This method is only meant to be called after receiving a READY packet.
     */
    public void purgeCache() {
        users.clear();
        servers.clear();
        groupChannels.clear();
        unavailableServers.clear();
        customEmojis.clear();
        messages.clear();
        timeOffset = null;
    }

    /**
     * Adds the given server to the cache.
     *
     * @param server The server to add.
     */
    public void addServerToCache(Server server) {
        servers.put(server.getId(), server);
    }

    /**
     * Removes the given server from the cache.
     *
     * @param serverId The id of the server to remove.
     */
    public void removeServerFromCache(long serverId) {
        servers.remove(serverId);
    }

    /**
     * Adds the given user to the cache.
     *
     * @param user The user to add.
     */
    public void addUserToCache(User user) {
        users.put(user.getId(), user);
    }

    /**
     * Adds a group channel to the cache.
     *
     * @param channel The channel to add.
     */
    public void addGroupChannelToCache(GroupChannel channel) {
        groupChannels.put(channel.getId(), channel);
    }

    /**
     * Adds a server id to the list with unavailable servers.
     *
     * @param serverId The id of the server.
     */
    public void addUnavailableServerToCache(long serverId) {
        unavailableServers.add(serverId);
    }

    /**
     * Removes a server id from the list with unavailable servers.
     *
     * @param serverId The id of the server.
     */
    public void removeUnavailableServerToCache(long serverId) {
        unavailableServers.remove(serverId);
    }

    /**
     * Sets the user of the connected account.
     *
     * @param yourself The user of the connected account.
     */
    public void setYourself(User yourself){
        you = yourself;
    }

    /**
     * Gets the time offset between the Discord time and our local time.
     * Might be <code>null</code> if it hasn't been calculated yet.
     *
     * @return The time offset between the Discord time and our local time.
     */
    public Long getTimeOffset() {
        return timeOffset;
    }

    /**
     * Sets the time offset between the Discord time and our local time.
     *
     * @param timeOffset The time offset to set.
     */
    public void setTimeOffset(Long timeOffset) {
        this.timeOffset = timeOffset;
    }

    /**
     * Gets a user or creates a new one from the given data.
     *
     * @param data The json data of the user.
     * @return The user.
     */
    public User getOrCreateUser(JSONObject data) {
        long id = Long.parseLong(data.getString("id"));
        synchronized (this) {
            return getUserById(id).orElseGet(() -> {
                if (!data.has("username")) {
                    throw new IllegalStateException("Couldn't get or created user. Please inform the developer!");
                }
                return new ImplUser(this, data);
            });
        }
    }

    /**
     * Gets or creates a new custom emoji object.
     *
     * @param server The server of the emoji.
     * @param data The data of the emoji.
     * @return The emoji for the given json object.
     */
    public CustomEmoji getOrCreateCustomEmoji(Server server, JSONObject data) {
        long id = Long.parseLong(data.getString("id"));
        return customEmojis.computeIfAbsent(id, key -> new ImplCustomEmoji(this, server, data));
    }

    /**
     * Gets or creates a new message object.
     *
     * @param channel The channel of the message.
     * @param data The data of the message.
     * @return The message for the given json object.
     */
    public Message getOrCreateMessage(TextChannel channel, JSONObject data) {
        long id = Long.parseLong(data.getString("id"));
        // The constructor already adds the message to the cache.
        // If we use #computeIfAbsent() here, it would cause a deadlock
        return messages.getOrDefault(id, new ImplMessage(this, channel, data));
    }

    /**
     * Adds a message to the cache.
     *
     * @param message The message to add.
     */
    public void addMessageToCache(Message message) {
        messages.computeIfAbsent(message.getId(), key -> message);
    }

    /**
     * Sets the current game, along with type and streaming Url.
     *
     * @param name The name of the game.
     * @param type The game's type.
     * @param streamingUrl The Url used for streaming.
     */
    private void updateGame(String name, GameType type, String streamingUrl){
        if (name == null) {
            game = null;
        } else if (streamingUrl == null) {
            game = new ImplGame(type, name, null);
        } else {
            game = new ImplGame(type, name, streamingUrl);
        }
        websocketAdapter.updateStatus();
    }

    /**
     * Adds a object listener.
     *
     * @param objectClass The class of the object.
     * @param objectId The id of the object.
     * @param listenerClass The listener class.
     * @param listener The listener to add.
     */
    public void addObjectListener(Class<?> objectClass, long objectId, Class<?> listenerClass, Object listener) {
        Map<Long, Map<Class<?>, List<Object>>> objectListener =
                objectListeners.computeIfAbsent(objectClass, key -> new ConcurrentHashMap<>());
        Map<Class<?>, List<Object>> listeners =
                objectListener.computeIfAbsent(objectId, key -> new ConcurrentHashMap<>());
        List<Object> classListeners = listeners.computeIfAbsent(listenerClass, c -> new ArrayList<>());
        classListeners.add(listener);
    }

    /**
     * Gets all message listeners of the given class.
     *
     * @param objectClass The class of the object.
     * @param objectId The id of the object.
     * @param listenerClass The listener class.
     * @param <T> The listener class.
     * @return A list with all object listeners of the given type.
     */
    @SuppressWarnings("unchecked") // We make sure it's the right type when adding elements
    public <T> List<T> getObjectListeners(Class<?> objectClass, long objectId, Class<?> listenerClass) {
        Map<Long, Map<Class<?>, List<Object>>> objectListener = objectListeners.get(objectClass);
        if (objectListener == null) {
            return Collections.emptyList();
        }
        Map<Class<?>, List<Object>> listeners = objectListener.get(objectId);
        if (listeners == null) {
            return Collections.emptyList();
        }
        List<Object> classListeners = listeners.getOrDefault(listenerClass, Collections.emptyList());
        return classListeners.stream().map(o -> (T) o).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Adds a listener.
     *
     * @param clazz The listener class.
     * @param listener The listener to add.
     */
    private void addListener(Class<?> clazz, Object listener) {
        List<Object> classListeners = listeners.computeIfAbsent(clazz, c -> new ArrayList<>());
        classListeners.add(listener);
    }

    /**
     * Gets all listeners of the given class.
     *
     * @param clazz The class of the listener.
     * @param <T> The class of the listener.
     * @return A list with all listeners of the given type.
     */
    @SuppressWarnings("unchecked") // We make sure it's the right type when adding elements
    private <T> List<T> getListeners(Class<?> clazz) {
        List<Object> classListeners = listeners.getOrDefault(clazz, new ArrayList<>());
        return classListeners.stream().map(o -> (T) o).collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public String getToken() {
        return token;
    }

    @Override
    public ThreadPool getThreadPool() {
        return threadPool;
    }

    @Override
    public RatelimitManager getRatelimitManager() {
        return ratelimitManager;
    }

    /*
     * Note: You might think the return type should be Optional<WebsocketAdapter>, because it's null till we receive
     *       the gateway from Discord. However the DiscordApi instance is only passed to the user, AFTER we connect
     *       so for the end user it is in fact never null.
     */
    @Override
    public DiscordWebsocketAdapter getWebSocketAdapter() {
        return websocketAdapter;
    }

    @Override
    public void setMessageCacheSize(int capacity, int storageTimeInSeconds) {
        this.defaultMessageCacheCapacity = capacity;
        this.defaultMessageCacheStorageTimeInSeconds = storageTimeInSeconds;
        getChannels().stream()
                .filter(channel -> channel instanceof TextChannel)
                .map(channel -> (TextChannel) channel)
                .forEach(channel -> {
                    channel.getMessageCache().setCapacity(capacity);
                    channel.getMessageCache().setStorageTimeInSeconds(storageTimeInSeconds);
                });
    }

    @Override
    public int getDefaultMessageCacheCapacity() {
        return defaultMessageCacheCapacity;
    }

    @Override
    public int getDefaultMessageCacheStorageTimeInSeconds() {
        return defaultMessageCacheStorageTimeInSeconds;
    }

    @Override
    public int getCurrentShard() {
        return currentShard;
    }

    @Override
    public int getTotalShards() {
        return totalShards;
    }

    @Override
    public void updateGame(String name) {
        updateGame(name, GameType.GAME, null);
    }

    @Override
    public void updateGame(String name, GameType type) {
        updateGame(name, type, null);
    }

    @Override
    public void updateGame(String name, String streamingUrl) {
        updateGame(name, GameType.STREAMING, streamingUrl);
    }

    @Override
    public Optional<Game> getGame() {
        return Optional.ofNullable(game);
    }

    @Override
    public User getYourself(){
        return you;
    }

    @Override
    public void disconnect() {
        websocketAdapter.disconnect();
        threadPool.shutdown();
    }

    @Override
    public void setReconnectDelay(Function<Integer, Integer> reconnectDelayProvider) {
        this.reconnectDelayProvider = reconnectDelayProvider;
    }

    @Override
    public int getReconnectDelay(int attempt) {
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be 1 or greater");
        }
        return reconnectDelayProvider.apply(attempt);
    }

    @Override
    public Collection<Long> getUnavailableServers() {
        return Collections.unmodifiableCollection(unavailableServers);
    }

    @Override
    public Collection<User> getUsers() {
        return users.values();
    }

    @Override
    public Optional<User> getUserById(long id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public Collection<Message> getCachedMessages() {
        return messages.values();
    }

    @Override
    public Optional<Message> getCachedMessageById(long id) {
        return Optional.ofNullable(messages.get(id));
    }

    @Override
    public Collection<Server> getServers() {
        return servers.values();
    }

    @Override
    public Optional<Server> getServerById(long id) {
        return Optional.ofNullable(servers.get(id));
    }

    @Override
    public Collection<CustomEmoji> getCustomEmojis() {
        return customEmojis.values();
    }

    @Override
    public Optional<CustomEmoji> getCustomEmojiById(long id) {
        return Optional.ofNullable(customEmojis.get(id));
    }

    @Override
    public Collection<GroupChannel> getGroupChannels() {
        return groupChannels.values();
    }

    @Override
    public Optional<GroupChannel> getGroupChannelById(long id) {
        return Optional.ofNullable(groupChannels.get(id));
    }

    @Override
    public void addMessageCreateListener(MessageCreateListener listener) {
        addListener(MessageCreateListener.class, listener);
    }

    @Override
    public List<MessageCreateListener> getMessageCreateListeners() {
        return getListeners(MessageCreateListener.class);
    }

    @Override
    public void addServerJoinListener(ServerJoinListener listener) {
        addListener(ServerJoinListener.class, listener);
    }

    @Override
    public List<ServerJoinListener> getServerJoinListeners() {
        return getListeners(ServerJoinListener.class);
    }

    @Override
    public void addServerLeaveListener(ServerLeaveListener listener) {
        addListener(ServerLeaveListener.class, listener);
    }

    @Override
    public List<ServerLeaveListener> getServerLeaveListeners() {
        return getListeners(ServerLeaveListener.class);
    }

    @Override
    public void addServerBecomesAvailableListener(ServerBecomesAvailableListener listener) {
        addListener(ServerBecomesAvailableListener.class, listener);
    }

    @Override
    public List<ServerBecomesAvailableListener> getServerBecomesAvailableListeners() {
        return getListeners(ServerBecomesAvailableListener.class);
    }

    @Override
    public void addServerBecomesUnavailableListener(ServerBecomesUnavailableListener listener) {
        addListener(ServerBecomesUnavailableListener.class, listener);
    }

    @Override
    public List<ServerBecomesUnavailableListener> getServerBecomesUnavailableListeners() {
        return getListeners(ServerBecomesUnavailableListener.class);
    }

    @Override
    public void addUserStartTypingListener(UserStartTypingListener listener) {
        addListener(UserStartTypingListener.class, listener);
    }

    @Override
    public List<UserStartTypingListener> getUserStartTypingListeners() {
        return getListeners(UserStartTypingListener.class);
    }

    @Override
    public void addServerChannelCreateListener(ServerChannelCreateListener listener) {
        addListener(ServerChannelCreateListener.class, listener);
    }

    @Override
    public List<ServerChannelCreateListener> getServerChannelCreateListeners() {
        return getListeners(ServerChannelCreateListener.class);
    }

    @Override
    public void addServerChannelDeleteListener(ServerChannelDeleteListener listener) {
        addListener(ServerChannelDeleteListener.class, listener);
    }

    @Override
    public List<ServerChannelDeleteListener> getServerChannelDeleteListeners() {
        return getListeners(ServerChannelDeleteListener.class);
    }

    @Override
    public void addMessageDeleteListener(MessageDeleteListener listener) {
        addListener(MessageDeleteListener.class, listener);
    }

    @Override
    public void addMessageDeleteListener(long messageId, MessageDeleteListener listener) {
        addObjectListener(Message.class, messageId, MessageDeleteListener.class, listener);
    }

    @Override
    public List<MessageDeleteListener> getMessageDeleteListeners() {
        return getListeners(MessageDeleteListener.class);
    }

    @Override
    public List<MessageDeleteListener> getMessageDeleteListeners(long messageId) {
        return getObjectListeners(Message.class, messageId, MessageDeleteListener.class);
    }

    @Override
    public void addMessageEditListener(MessageEditListener listener) {
        addListener(MessageEditListener.class, listener);
    }

    @Override
    public void addMessageEditListener(long messageId, MessageEditListener listener) {
        addObjectListener(Message.class, messageId, MessageEditListener.class, listener);
    }

    @Override
    public List<MessageEditListener> getMessageEditListeners() {
        return getListeners(MessageEditListener.class);
    }

    @Override
    public List<MessageEditListener> getMessageEditListeners(long messageId) {
        return getObjectListeners(Message.class, messageId, MessageEditListener.class);
    }

    @Override
    public void addReactionAddListener(ReactionAddListener listener) {
        addListener(ReactionAddListener.class, listener);
    }

    @Override
    public void addReactionAddListener(long messageId, ReactionAddListener listener) {
        addObjectListener(Message.class, messageId, ReactionAddListener.class, listener);
    }

    @Override
    public List<ReactionAddListener> getReactionAddListeners() {
        return getListeners(ReactionAddListener.class);
    }

    @Override
    public List<ReactionAddListener> getReactionAddListeners(long messageId) {
        return getObjectListeners(Message.class, messageId, ReactionAddListener.class);
    }

    @Override
    public void addReactionRemoveListener(ReactionRemoveListener listener) {
        addListener(ReactionRemoveListener.class, listener);
    }

    @Override
    public void addReactionRemoveListener(long messageId, ReactionRemoveListener listener) {
        addObjectListener(Message.class, messageId, ReactionRemoveListener.class, listener);
    }

    @Override
    public List<ReactionRemoveListener> getReactionRemoveListeners() {
        return getListeners(ReactionRemoveListener.class);
    }

    @Override
    public List<ReactionRemoveListener> getReactionRemoveListeners(long messageId) {
        return getObjectListeners(Message.class, messageId, ReactionRemoveListener.class);
    }

    @Override
    public void addServerMemberAddListener(ServerMemberAddListener listener) {
        addListener(ServerMemberAddListener.class, listener);
    }

    @Override
    public List<ServerMemberAddListener> getServerMemberAddListeners() {
        return getListeners(ServerMemberAddListener.class);
    }

    @Override
    public void addServerMemberRemoveListener(ServerMemberRemoveListener listener) {
        addListener(ServerMemberRemoveListener.class, listener);
    }

    @Override
    public List<ServerMemberRemoveListener> getServerMemberRemoveListeners() {
        return getListeners(ServerMemberRemoveListener.class);
    }

    @Override
    public void addServerChangeNameListener(ServerChangeNameListener listener) {
        addListener(ServerChangeNameListener.class, listener);
    }

    @Override
    public List<ServerChangeNameListener> getServerChangeNameListeners() {
        return getListeners(ServerChangeNameListener.class);
    }

    @Override
    public void addServerChannelChangeNameListener(ServerChannelChangeNameListener listener) {
        addListener(ServerChannelChangeNameListener.class, listener);
    }

    @Override
    public List<ServerChannelChangeNameListener> getServerChannelChangeNameListeners() {
        return getListeners(ServerChannelChangeNameListener.class);
    }

    @Override
    public void addServerChannelChangePositionListener(ServerChannelChangePositionListener listener) {
        addListener(ServerChannelChangePositionListener.class, listener);
    }

    @Override
    public List<ServerChannelChangePositionListener> getServerChannelChangePositionListeners() {
        return getListeners(ServerChannelChangePositionListener.class);
    }

    @Override
    public void addCustomEmojiCreateListener(CustomEmojiCreateListener listener) {
        addListener(CustomEmojiCreateListener.class, listener);
    }

    @Override
    public List<CustomEmojiCreateListener> getCustomEmojiCreateListeners() {
        return getListeners(CustomEmojiCreateListener.class);
    }

    @Override
    public void addUserChangeGameListener(UserChangeGameListener listener) {
        addListener(UserChangeGameListener.class, listener);
    }

    @Override
    public List<UserChangeGameListener> getUserChangeGameListeners() {
        return getListeners(UserChangeGameListener.class);
    }

    @Override
    public void addUserChangeStatusListener(UserChangeStatusListener listener) {
        addListener(UserChangeStatusListener.class, listener);
    }

    @Override
    public List<UserChangeStatusListener> getUserChangeStatusListeners() {
        return getListeners(UserChangeStatusListener.class);
    }

    @Override
    public void addRoleChangePermissionsListener(RoleChangePermissionsListener listener) {
        addListener(RoleChangePermissionsListener.class, listener);
    }

    @Override
    public List<RoleChangePermissionsListener> getRoleChangePermissionsListeners() {
        return getListeners(RoleChangePermissionsListener.class);
    }

    @Override
    public void addRoleChangePositionListener(RoleChangePositionListener listener) {
        addListener(RoleChangePositionListener.class, listener);
    }

    @Override
    public List<RoleChangePositionListener> getRoleChangePositionListeners() {
        return getListeners(RoleChangePositionListener.class);
    }

    @Override
    public void addServerChannelChangeOverwrittenPermissionsListener(
            ServerChannelChangeOverwrittenPermissionsListener listener) {
        addListener(ServerChannelChangeOverwrittenPermissionsListener.class, listener);
    }

    @Override
    public List<ServerChannelChangeOverwrittenPermissionsListener> getServerChannelChangeOverwrittenPermissionsListeners() {
        return getListeners(ServerChannelChangeOverwrittenPermissionsListener.class);
    }

    @Override
    public void addRoleCreateListener(RoleCreateListener listener) {
        addListener(RoleCreateListener.class, listener);
    }

    @Override
    public List<RoleCreateListener> getRoleCreateListeners() {
        return getListeners(RoleCreateListener.class);
    }

    @Override
    public void addRoleDeleteListener(RoleDeleteListener listener) {
        addListener(RoleDeleteListener.class, listener);
    }

    @Override
    public List<RoleDeleteListener> getRoleDeleteListeners() {
        return getListeners(RoleDeleteListener.class);
    }
    
    @Override
    public List<UserRoleRemoveListener> getUserRoleRemoveListener() {
        return getListeners(UserRoleRemoveListener.class);
    }
    
    @Override
    public List<UserRoleAddListener> getUserRoleAddListener() {
        return getListeners(UserRoleAddListener.class);
    }
    

    @Override
    public void addUserChangeNicknameListener(UserChangeNicknameListener listener) {
        addListener(UserChangeNicknameListener.class, listener);
    }

    @Override
    public List<UserChangeNicknameListener> getUserChangeNicknameListeners() {
        return getListeners(UserChangeNicknameListener.class);
    }

    @Override
    public void addLostConnectionListener(LostConnectionListener listener) {
        addListener(LostConnectionListener.class, listener);
    }

    @Override
    public List<LostConnectionListener> getLostConnectionListeners() {
        return getListeners(LostConnectionListener.class);
    }

    @Override
    public void addReconnectListener(ReconnectListener listener) {
        addListener(ReconnectListener.class, listener);
    }

    @Override
    public List<ReconnectListener> getReconnectListeners() {
        return getListeners(ReconnectListener.class);
    }

    @Override
    public void addResumeListener(ResumeListener listener) {
        addListener(ResumeListener.class, listener);
    }

    @Override
    public List<ResumeListener> getResumeListeners() {
        return getListeners(ResumeListener.class);
    }

    @Override
    public void addServerTextChannelChangeTopicListener(ServerTextChannelChangeTopicListener listener) {
        addListener(ServerTextChannelChangeTopicListener.class, listener);
    }

    @Override
    public List<ServerTextChannelChangeTopicListener> getServerTextChannelChangeTopicListeners() {
        return getListeners(ServerTextChannelChangeTopicListener.class);
    }
}
