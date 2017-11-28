package de.btobastian.javacord.entities.impl;

import de.btobastian.javacord.DiscordApi;
import de.btobastian.javacord.ExplicitContentFilterLevel;
import de.btobastian.javacord.ImplDiscordApi;
import de.btobastian.javacord.entities.*;
import de.btobastian.javacord.entities.channels.ChannelCategory;
import de.btobastian.javacord.entities.channels.ServerChannel;
import de.btobastian.javacord.entities.channels.ServerTextChannel;
import de.btobastian.javacord.entities.channels.ServerVoiceChannel;
import de.btobastian.javacord.entities.channels.impl.ImplChannelCategory;
import de.btobastian.javacord.entities.channels.impl.ImplServerTextChannel;
import de.btobastian.javacord.entities.channels.impl.ImplServerVoiceChannel;
import de.btobastian.javacord.entities.message.emoji.CustomEmoji;
import de.btobastian.javacord.entities.permissions.Role;
import de.btobastian.javacord.entities.permissions.impl.ImplRole;
import de.btobastian.javacord.utils.logging.LoggerUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The implementation of {@link de.btobastian.javacord.entities.Server}.
 */
public class ImplServer implements Server {

    /**
     * The logger of this class.
     */
    private static final Logger logger = LoggerUtil.getLogger(ImplServer.class);

    /**
     * The discord api instance.
     */
    private final ImplDiscordApi api;

    /**
     * The id of the server.
     */
    private final long id;

    /**
     * The name of the server.
     */
    private String name;

    /**
     * The region of the server.
     */
    private Region region;

    /**
     * Whether the server is considered as large or not.
     */
    private boolean large;

    /**
     * The id of the owner.
     */
    private long ownerId;

    /**
     * The verification level of the server.
     */
    private VerificationLevel verificationLevel;

    /**
     * The explicit content filter level of the server.
     */
    private ExplicitContentFilterLevel explicitContentFilterLevel;

    /**
     * The default message notification level of the server.
     */
    private DefaultMessageNotificationLevel defaultMessageNotificationLevel;

    /**
     * The amount of members in this server.
     */
    private int memberCount = -1;

    /**
     * The icon id of the server. Might be <code>null</code>.
     */
    private String iconId;

    /**
     * The splash of the server. Might be <code>null</code>.
     */
    private String splash;

    /**
     * A map with all roles of the server.
     */
    private final ConcurrentHashMap<Long, Role> roles = new ConcurrentHashMap<>();

    /**
     * A map with all channels of the server.
     */
    private final ConcurrentHashMap<Long, ServerChannel> channels = new ConcurrentHashMap<>();

    /**
     * A map with all members of the server.
     */
    private final ConcurrentHashMap<Long, User> members = new ConcurrentHashMap<>();

    /**
     * A map with all nicknames. The key is the user id.
     */
    private final ConcurrentHashMap<Long, String> nicknames = new ConcurrentHashMap<>();

    /**
     * A list with all custom emojis from this server.
     */
    private final Collection<CustomEmoji> customEmojis = new ArrayList<>();

    /**
     * Creates a new server object.
     *
     * @param api The discord api instance.
     * @param data The json data of the server.
     */
    public ImplServer(ImplDiscordApi api, JSONObject data) {
        this.api = api;

        id = Long.parseLong(data.getString("id"));
        name = data.getString("name");
        region = Region.getRegionByKey(data.getString("region"));
        large = data.getBoolean("large");
        memberCount = data.getInt("member_count");
        ownerId = Long.parseLong(data.getString("owner_id"));
        verificationLevel = VerificationLevel.fromId(data.getInt("verification_level"));
        explicitContentFilterLevel = ExplicitContentFilterLevel.fromId(data.getInt("explicit_content_filter"));
        defaultMessageNotificationLevel =
                DefaultMessageNotificationLevel.fromId(data.getInt("default_message_notifications"));
        if (data.has("icon") && !data.isNull("icon")) {
            iconId = data.getString("icon");
        }
        if (data.has("splash") && !data.isNull("splash")) {
            splash = data.getString("splash");
        }

        if (data.has("channels")) {
            JSONArray channels = data.getJSONArray("channels");
            for (int i = 0; i < channels.length(); i++) {
                JSONObject channel = channels.getJSONObject(i);
                switch (channel.getInt("type")) {
                    case 0:
                        getOrCreateServerTextChannel(channel);
                        break;
                    case 2:
                        getOrCreateServerVoiceChannel(channel);
                        break;
                    case 4:
                        getOrCreateChannelCategory(channel);
                        break;
                }
            }
        }

        JSONArray roles = data.has("roles") ? data.getJSONArray("roles") : new JSONArray();
        for (int i = 0; i < roles.length(); i++) {
            Role role = new ImplRole(api, this, roles.getJSONObject(i));
            this.roles.put(role.getId(), role);
        }

        JSONArray members = new JSONArray();
        if (data.has("members")) {
            members = data.getJSONArray("members");
        }
        addMembers(members);

        if (isLarge() && getMembers().size() < getMemberCount()) {
            JSONObject requestGuildMembersPacket = new JSONObject()
                    .put("op", 8)
                    .put("d", new JSONObject()
                            .put("guild_id", String.valueOf(getId()))
                            .put("query","")
                            .put("limit", 0));
            logger.debug("Sending request guild members packet for server {}", this);
            this.api.getWebSocketAdapter().getWebSocket().sendText(requestGuildMembersPacket.toString());
        }

        JSONArray emojis = data.has("emojis") ? data.getJSONArray("emojis") : new JSONArray();
        for (int i = 0; i < emojis.length(); i++) {
            CustomEmoji emoji = api.getOrCreateCustomEmoji(this, emojis.getJSONObject(i));
            addCustomEmoji(emoji);
        }

        JSONArray presences = data.has("presences") ? data.getJSONArray("presences") : new JSONArray();
        for (int i = 0; i < presences.length(); i++) {
            JSONObject presence = presences.getJSONObject(i);
            long userId = Long.parseLong(presence.getJSONObject("user").getString("id"));
            api.getUserById(userId).map(user -> ((ImplUser) user)).ifPresent(user -> {
                if (presence.has("game")) {
                    Game game = null;
                    if (!presence.isNull("game")) {
                        int gameType = presence.getJSONObject("game").getInt("type");
                        String name = presence.getJSONObject("game").getString("name");
                        String streamingUrl =
                                presence.getJSONObject("game").has("url") &&
                                !presence.getJSONObject("game").isNull("url") ?
                                presence.getJSONObject("game").getString("url") : null;
                        game = new ImplGame(GameType.getGameTypeById(gameType), name, streamingUrl);
                    }
                    user.setGame(game);
                }
                if (presence.has("status")) {
                    UserStatus status = UserStatus.fromString(presence.optString("status"));
                    user.setStatus(status);
                }
            });
        }

        api.addServerToCache(this);
    }

    /**
     * Adds a channel to the cache.
     *
     * @param channel The channel to add.
     */
    public void addChannelToCache(ServerChannel channel) {
        channels.put(channel.getId(), channel);
    }

    /**
     * Removes a channel from the cache.
     *
     * @param channelId The id of the channel to remove.
     */
    public void removeChannelFromCache(long channelId) {
        channels.remove(channelId);
    }

    /**
     * Removes a role from the cache.
     *
     * @param roleId The id of the role to remove.
     */
    public void removeRole(long roleId) {
        roles.remove(roleId);
    }

    /**
     * Adds a custom emoji.
     *
     * @param emoji The emoji to add.
     */
    public void addCustomEmoji(CustomEmoji emoji) {
        customEmojis.add(emoji);
    }

    /**
     * Removes a custom emoji.
     *
     * @param emoji The emoji to remove.
     */
    public void removeCustomEmoji(CustomEmoji emoji) {
        customEmojis.remove(emoji);
    }

    /**
     * Gets or create a new role.
     *
     * @param data The json data of the role.
     * @return The role.
     */
    public Role getOrCreateRole(JSONObject data) {
        long id = Long.parseLong(data.getString("id"));
        synchronized (this) {
            return getRoleById(id).orElseGet(() -> {
                Role role = new ImplRole(api, this, data);
                this.roles.put(role.getId(), role);
                return role;
            });
        }
    }

    /**
     * Gets or creates a channel category.
     *
     * @param data The json data of the channel.
     * @return The server text channel.
     */
    public ChannelCategory getOrCreateChannelCategory(JSONObject data) {
        long id = Long.parseLong(data.getString("id"));
        int type = data.getInt("type");
        synchronized (this) {
            if (type == 4) {
                return getChannelCategoryById(id).orElseGet(() -> new ImplChannelCategory(api, this, data));
            }
        }
        // Invalid channel type
        return null;
    }

    /**
     * Gets or creates a server text channel.
     *
     * @param data The json data of the channel.
     * @return The server text channel.
     */
    public ServerTextChannel getOrCreateServerTextChannel(JSONObject data) {
        long id = Long.parseLong(data.getString("id"));
        int type = data.getInt("type");
        synchronized (this) {
            if (type == 0) {
                return getTextChannelById(id).orElseGet(() -> new ImplServerTextChannel(api, this, data));
            }
        }
        // Invalid channel type
        return null;
    }

    /**
     * Gets or creates a server voice channel.
     *
     * @param data The json data of the channel.
     * @return The server voice channel.
     */
    public ServerVoiceChannel getOrCreateServerVoiceChannel(JSONObject data) {
        long id = Long.parseLong(data.getString("id"));
        int type = data.getInt("type");
        synchronized (this) {
            if (type == 2) {
                return getVoiceChannelById(id).orElseGet(() -> new ImplServerVoiceChannel(api, this, data));
            }
        }
        // Invalid channel type
        return null;
    }

    /**
     * Removes a member from the server.
     *
     * @param user The user to remove.
     */
    public void removeMember(User user) {
        members.remove(user.getId());
        nicknames.remove(user.getId());
        getRoles().forEach(role -> ((ImplRole) role).removeUserFromCache(user));
    }

    /**
     * Adds a member to the server.
     *
     * @param member The user to add.
     */
    public void addMember(JSONObject member) {
        User user = api.getOrCreateUser(member.getJSONObject("user"));
        members.put(user.getId(), user);
        if (member.has("nick") && !member.isNull("nick")) {
            nicknames.put(user.getId(), member.getString("nick"));
        }

        JSONArray memberRoles = member.getJSONArray("roles");
        for (int i = 0; i < memberRoles.length(); i++) {
            long roleId = Long.parseLong(memberRoles.getString(i));
            getRoleById(roleId).map(role -> ((ImplRole) role)).ifPresent(role -> role.addUserToCache(user));
        }
    }

    /**
     * Sets the nickname of the user.
     *
     * @param user The user.
     * @param nickname The nickname to set.
     */
    public void setNickname(User user, String nickname) {
        if (nickname == null) {
            nicknames.remove(user.getId());
        } else {
            nicknames.put(user.getId(), nickname);
        }
    }

    /**
     * Adds members to the server.
     *
     * @param members An array of guild member objects.
     */
    public void addMembers(JSONArray members) {
        for (int i = 0; i < members.length(); i++) {
            addMember(members.getJSONObject(i));
        }
    }

    /**
     * Sets the name of the server.
     *
     * @param name The name of the server.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets an unordered collection with all channels in the server.
     *
     * @return An unordered collection with all channels in the server.
     */
    public Collection<ServerChannel> getUnorderedChannels() {
        return channels.values();
    }

    @Override
    public DiscordApi getApi() {
        return api;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Region getRegion() {
        return region;
    }

    @Override
    public Optional<String> getNickname(User user) {
        return Optional.ofNullable(nicknames.get(user.getId()));
    }

    @Override
    public Collection<User> getMembers() {
        return members.values();
    }

    @Override
    public boolean isLarge() {
        return large;
    }

    @Override
    public int getMemberCount() {
        return memberCount;
    }

    @Override
    public User getOwner() {
        return api.getUserById(ownerId)
                .orElseThrow(() -> new IllegalStateException("Owner of server " + toString() + " is not cached!"));
    }

    @Override
    public VerificationLevel getVerificationLevel() {
        return verificationLevel;
    }

    @Override
    public ExplicitContentFilterLevel getExplicitContentFilterLevel() {
        return explicitContentFilterLevel;
    }

    @Override
    public DefaultMessageNotificationLevel getDefaultMessageNotificationLevel() {
        return defaultMessageNotificationLevel;
    }

    @Override
    public Optional<Icon> getIcon() {
        if (iconId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ImplIcon(
                    getApi(), new URL("https://cdn.discordapp.com/icons/" + getId() + "/" + iconId + ".png")));
        } catch (MalformedURLException e) {
            logger.warn("Seems like the url of the icon is malformed! Please contact the developer!", e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Icon> getSplash() {
        if (splash == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ImplIcon(
                    getApi(), new URL("https://cdn.discordapp.com/splashes/" + getId() + "/" + splash + ".png")));
        } catch (MalformedURLException e) {
            logger.warn("Seems like the url of the icon is malformed! Please contact the developer!", e);
            return Optional.empty();
        }
    }

    @Override
    public List<Role> getRoles() {
        return roles.values().stream()
                .sorted(Comparator.comparingInt(Role::getPosition))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Role> getRoleById(long id) {
        return Optional.ofNullable(roles.get(id));
    }

    @Override
    public Collection<CustomEmoji> getCustomEmojis() {
        return Collections.unmodifiableCollection(customEmojis);
    }

    @Override
    public List<ServerChannel> getChannels() {
        Collection<ServerChannel> channelsUnordered = this.channels.values();
        List<ServerChannel> channels = new ArrayList<>();
        channelsUnordered.stream()
                .filter(channel -> !channel.asChannelCategory().isPresent())
                .filter(channel -> channel.asServerTextChannel().isPresent())
                .filter(channel -> !channel.asServerTextChannel().get().getCategory().isPresent())
                .sorted(Comparator.comparingInt(ServerChannel::getRawPosition))
                .forEachOrdered(channels::add);
        channelsUnordered.stream()
                .filter(channel -> !channel.asChannelCategory().isPresent())
                .filter(channel -> channel.asServerVoiceChannel().isPresent())
                .filter(channel -> !channel.asServerVoiceChannel().get().getCategory().isPresent())
                .sorted(Comparator.comparingInt(ServerChannel::getRawPosition))
                .forEachOrdered(channels::add);
        getChannelCategories().forEach(category -> {
                    channels.add(category);
                    channels.addAll(category.getChannels());
                });
        return channels;
    }

    @Override
    public Optional<ServerChannel> getChannelById(long id) {
        return Optional.ofNullable(channels.get(id));
    }

    @Override
    public String toString() {
        return String.format("Server (id: %s, name: %s)", getId(), getName());
    }

}
