package de.btobastian.javacord.entities;

import com.mashape.unirest.http.HttpMethod;
import de.btobastian.javacord.ImplDiscordApi;
import de.btobastian.javacord.entities.channels.*;
import de.btobastian.javacord.entities.impl.ImplServer;
import de.btobastian.javacord.entities.message.emoji.CustomEmoji;
import de.btobastian.javacord.entities.permissions.*;
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
import de.btobastian.javacord.listeners.user.UserChangeGameListener;
import de.btobastian.javacord.listeners.user.UserChangeNicknameListener;
import de.btobastian.javacord.listeners.user.UserChangeStatusListener;
import de.btobastian.javacord.listeners.user.UserStartTypingListener;
import de.btobastian.javacord.utils.rest.RestEndpoint;
import de.btobastian.javacord.utils.rest.RestRequest;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * The class represents a Discord server, sometimes also called guild.
 */
public interface Server extends DiscordEntity, IconHolder {

    /**
     * Gets the name of the server.
     *
     * @return The name of the server.
     */
    String getName();

    /**
     * Gets the region of the server.
     *
     * @return The region of the server.
     */
    Region getRegion();

    /**
     * Gets the nickname of a user.
     *
     * @param user The user to check.
     * @return The nickname of the user.
     */
    Optional<String> getNickname(User user);

    /**
     * Gets a collection with all members of the server.
     *
     * @return A collection with all members of the server.
     */
    Collection<User> getMembers();

    /**
     * Checks if the server if considered large.
     *
     * @return Whether the server is large or not.
     */
    boolean isLarge();

    /**
     * Gets the amount of members in this server.
     *
     * @return The amount of members in this server.
     */
    int getMemberCount();

    /**
     * Gets the owner of the server.
     *
     * @return The owner of the server.
     */
    User getOwner();

    /**
     * Gets a sorted list (by position) with all roles of the server.
     *
     * @return A sorted list (by position) with all roles of the server.
     */
    List<Role> getRoles();

    /**
     * Gets a role by it's id.
     *
     * @param id The id of the role.
     * @return The role with the given id.
     */
    Optional<Role> getRoleById(long id);

    /**
     * Gets a role by it's id.
     *
     * @param id The id of the role.
     * @return The role with the given id.
     */
    default Optional<Role> getRoleById(String id) {
        try {
            return getRoleById(Long.parseLong(id));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Gets a sorted list (by position) with all roles with the given name.
     * This method is case sensitive!
     *
     * @param name The name of the roles.
     * @return A sorted list (by position) with all roles with the given name.
     */
    default List<Role> getRolesByName(String name) {
        return getRoles().stream()
                .filter(role -> role.getName().equals(name))
                .collect(Collectors.toList());
    }

    /**
     * Gets a sorted list (by position) with all roles with the given name.
     * This method is case insensitive!
     *
     * @param name The name of the roles.
     * @return A sorted list (by position) with all roles with the given name.
     */
    default List<Role> getRolesByNameIgnoreCase(String name) {
        return getRoles().stream()
                .filter(role -> role.getName().equalsIgnoreCase(name))
                .collect(Collectors.toList());
    }

    /**
     * Gets a sorted list (by position) with all roles of the user in the server.
     *
     * @param user The user.
     * @return A sorted list (by position) with all roles of the user in the server.
     */
    default List<Role> getRolesOf(User user) {
        return getRoles().stream()
                .filter(role -> role.getUsers().contains(user))
                .collect(Collectors.toList());
    }

    /**
     * Gets the permissions of a user.
     *
     * @param user The user.
     * @return The permissions of the user.
     */
    default Permissions getPermissionsOf(User user) {
        PermissionsBuilder builder = new PermissionsBuilder();
        getAllowedPermissionsOf(user).forEach(type -> builder.setState(type, PermissionState.ALLOWED));
        return builder.build();
    }

    /**
     * Get the allowed permissions of a given user.
     * Remember, that some permissions affect others!
     * E.g. a user who has {@link PermissionType#SEND_MESSAGES} but not {@link PermissionType#READ_MESSAGES} cannot
     * send messages, even though he has the {@link PermissionType#SEND_MESSAGES} permission.
     *
     * @param user The user.
     * @return The allowed permissions of the given user.
     */
    default Collection<PermissionType> getAllowedPermissionsOf(User user) {
        Collection<PermissionType> allowed = new HashSet<>();
        if (getOwner() == user) {
            allowed.addAll(Arrays.asList(PermissionType.values()));
        } else {
            getRolesOf(user).forEach(role -> allowed.addAll(role.getAllowedPermissions()));
        }
        return allowed;
    }

    /**
     * Get the unset permissions of a given user.
     *
     * @param user The user.
     * @return The unset permissions of the given user.
     */
    default Collection<PermissionType> getUnsetPermissionsOf(User user) {
        Collection<PermissionType> unset = new HashSet<>();
        if (getOwner() == user) {
            return unset;
        }
        getRolesOf(user).forEach(role -> unset.addAll(role.getUnsetPermissions()));
        return unset;
    }

    /**
     * Checks if the user has a given set of permissions.
     *
     * @param user The user to check.
     * @param type The permission type(s) to check.
     * @return Whether the user has all given permissions of not.
     * @see #getAllowedPermissionsOf(User)
     */
    default boolean hasPermissions(User user, PermissionType... type) {
        return getAllowedPermissionsOf(user).containsAll(Arrays.asList(type));
    }

    default CompletableFuture<Void> kickUser(User user){
        return new RestRequest<Void>(getApi(), HttpMethod.DELETE, RestEndpoint.SERVER_MEMBER)
                .setUrlParameters(String.valueOf(getId()), String.valueOf(user.getId()))
                .execute(res -> null);
    }

    default CompletableFuture<Void> banUser(User user){
        return banUser(user, 0);
    }

    default CompletableFuture<Void> banUser(User user, int deleteMessageDays){
        return new RestRequest<Void>(getApi(), HttpMethod.PUT, RestEndpoint.BANS)
                .setUrlParameters(String.valueOf(getId()), String.valueOf(user.getId()), String.valueOf(deleteMessageDays))
                .execute(res -> null);
    }

    /**
     * Changes the nickname of the given user.
     *
     * @param user The user.
     * @param nickname The new nickname of the user.
     * @return A future to check if the update was successful.
     */
    default CompletableFuture<Void> updateNickname(User user, String nickname) {
        if (user.isYourself()) {
            return new RestRequest<Void>(getApi(), HttpMethod.PATCH, RestEndpoint.OWN_NICKNAME)
                    .setUrlParameters(String.valueOf(getId()))
                    .setBody(new JSONObject().put("nick", nickname == null ? JSONObject.NULL : nickname))
                    .execute(res -> null);
        } else {
            return new RestRequest<Void>(getApi(), HttpMethod.PATCH, RestEndpoint.SERVER_MEMBER)
                    .setUrlParameters(String.valueOf(getId()), String.valueOf(user.getId()))
                    .setBody(new JSONObject().put("nick", nickname == null ? JSONObject.NULL : nickname))
                    .execute(res -> null);
        }
    }

    /**
     * Removes the nickname of the given user.
     *
     * @param user The user.
     * @return A future to check if the update was successful.
     */
    default CompletableFuture<Void> resetNickname(User user) {
        return updateNickname(user, null);
    }

    /**
     * Updates the name of the server.
     *
     * @param name The new name of the server.
     * @return A future to check if the update was successful.
     */
    default CompletableFuture<Void> updateName(String name) {
        return new RestRequest<Void>(getApi(), HttpMethod.PATCH, RestEndpoint.SERVER)
                .setUrlParameters(String.valueOf(getId()))
                .setBody(new JSONObject().put("name", name == null ? JSONObject.NULL : name))
                .execute(res -> null);
    }

    /**
     * Updates the region of the server.
     *
     * @param region The new region of the server.
     * @return A future to check if the update was successful.
     */
    default CompletableFuture<Void> updateRegion(Region region) {
        return new RestRequest<Void>(getApi(), HttpMethod.PATCH, RestEndpoint.SERVER)
                .setUrlParameters(String.valueOf(getId()))
                .setBody(new JSONObject().put("region", region == null ? JSONObject.NULL : region.getKey()))
                .execute(res -> null);
    }

    /**
     * Sets the afk timeout (in seconds) of the server.
     *
     * @param seconds The timeout in seconds.
     * @return A future to check if the update was successful.
     */
    default CompletableFuture<Void> updateAfkTimeout(int seconds) {
        return new RestRequest<Void>(getApi(), HttpMethod.PATCH, RestEndpoint.SERVER)
                .setUrlParameters(String.valueOf(getId()))
                .setBody(new JSONObject().put("afk_timeout", seconds))
                .execute(res -> null);
    }

    /**
     * Transfers the ownership of the server to an other user.
     * You must be the owner of this server in order to transfer it!
     *
     * @param newOwner The new owner of the server.
     * @return A future to check if the update was successful.
     */
    default CompletableFuture<Void> transferOwnership(User newOwner) {
        return new RestRequest<Void>(getApi(), HttpMethod.PATCH, RestEndpoint.SERVER)
                .setUrlParameters(String.valueOf(getId()))
                .setBody(new JSONObject()
                        .put("owner_id", newOwner == null ? JSONObject.NULL : String.valueOf(newOwner.getId())))
                .execute(res -> null);
    }

    /**
     * Checks if a user has a given permission.
     * Remember, that some permissions affect others!
     * E.g. a user who has {@link PermissionType#SEND_MESSAGES} but not {@link PermissionType#READ_MESSAGES} cannot
     * send messages, even though he has the {@link PermissionType#SEND_MESSAGES} permission.
     * This method also do not take into account overwritten permissions in some channels!
     *
     * @param user The user.
     * @param permission The permission to check.
     * @return Whether the user has the permission or not.
     */
    default boolean hasPermission(User user, PermissionType permission) {
        return getAllowedPermissionsOf(user).contains(permission);
    }

    /**
     * Gets a collection with all custom emojis of this server.
     *
     * @return A collection with all custom emojis of this server.
     */
    Collection<CustomEmoji> getCustomEmojis();

    /**
     * Gets a custom emoji in this server by it's id.
     *
     * @param id The id of the emoji.
     * @return The emoji with the given id.
     */
    default Optional<CustomEmoji> getCustomEmojiById(long id) {
        return getCustomEmojis().stream().filter(emoji -> emoji.getId() == id).findAny();
    }

    /**
     * Gets a custom emoji in this server by it's id.
     *
     * @param id The id of the emoji.
     * @return The emoji with the given id.
     */
    default Optional<CustomEmoji> getCustomEmojiById(String id) {
        try {
            return getCustomEmojiById(Long.parseLong(id));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Gets a collection of all custom emojis with the given name in the server.
     * This method is case sensitive!
     *
     * @param name The name of the custom emojis.
     * @return A collection of all custom emojis with the given name in this server.
     */
    default Collection<CustomEmoji> getCustomEmojisByName(String name) {
        return getCustomEmojis().stream()
                .filter(emoji -> emoji.getName().equals(name))
                .collect(Collectors.toList());
    }

    /**
     * Gets a collection of all custom emojis with the given name in the server.
     * This method is case insensitive!
     *
     * @param name The name of the custom emojis.
     * @return A collection of all custom emojis with the given name in this server.
     */
    default Collection<CustomEmoji> getCustomEmojisByNameIgnoreCase(String name) {
        return getCustomEmojis().stream()
                .filter(emoji -> emoji.getName().equalsIgnoreCase(name))
                .collect(Collectors.toList());
    }

    /**
     * Gets a new channel category builder.
     *
     * @return The builder to create a new channel category.
     */
    default ChannelCategoryBuilder getChannelCategoryBuilder() {
        return new ChannelCategoryBuilder(this);
    }

    /**
     * Gets a new server text channel builder.
     *
     * @return The builder to create a new server text channel.
     */
    default ServerTextChannelBuilder getTextChannelBuilder() {
        return new ServerTextChannelBuilder(this);
    }

    /**
     * Gets a new server voice channel builder.
     *
     * @return The builder to create a new server voice channel.
     */
    default ServerVoiceChannelBuilder getVoiceChannelBuilder() {
        return new ServerVoiceChannelBuilder(this);
    }

    /**
     * Gets a sorted list (by position) with all channels of the server.
     *
     * @return A sorted list (by position) with all channels of the server.
     */
    List<ServerChannel> getChannels();

    /**
     * Gets a sorted list (by position) with all channel categories of the server.
     *
     * @return A sorted list (by position) with all channel categories of the server.
     */
    default List<ChannelCategory> getChannelCategories() {
        return ((ImplServer) this).getUnorderedChannels().stream()
                .filter(channel -> channel instanceof ChannelCategory)
                .sorted(Comparator.comparingInt(ServerChannel::getRawPosition))
                .map(channel -> (ChannelCategory) channel)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Gets a sorted list (by position) with all text channels of the server.
     *
     * @return A sorted list (by position) with all text channels of the server.
     */
    default List<ServerTextChannel> getTextChannels() {
        return ((ImplServer) this).getUnorderedChannels().stream()
                .filter(channel -> channel instanceof ServerTextChannel)
                .sorted(Comparator.comparingInt(ServerChannel::getRawPosition))
                .map(channel -> (ServerTextChannel) channel)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Gets a sorted list (by position) with all voice channels of the server.
     *
     * @return A sorted list (by position) with all voice channels of the server.
     */
    default List<ServerVoiceChannel> getVoiceChannels() {
        return ((ImplServer) this).getUnorderedChannels().stream()
                .filter(channel -> channel instanceof ServerVoiceChannel)
                .sorted(Comparator.comparingInt(ServerChannel::getRawPosition))
                .map(channel -> (ServerVoiceChannel) channel)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Gets a channel by it's id.
     *
     * @param id The id of the channel.
     * @return The channel with the given id.
     */
    Optional<ServerChannel> getChannelById(long id);

    /**
     * Gets a channel by it's id.
     *
     * @param id The id of the channel.
     * @return The channel with the given id.
     */
    default Optional<ServerChannel> getChannelById(String id) {
        try {
            return getChannelById(Long.valueOf(id));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Gets a sorted list (by position) with all channels with the given name.
     * This method is case sensitive!
     *
     * @param name The name of the channels.
     * @return A sorted list (by position) with all channels with the given name.
     */
    default List<ServerChannel> getChannelsByName(String name) {
        return getChannels().stream()
                .filter(channel -> channel.getName().equals(name))
                .collect(Collectors.toList());
    }

    /**
     * Gets a sorted list (by position) with all channels with the given name.
     * This method is case insensitive!
     *
     * @param name The name of the channels.
     * @return A sorted list (by position) with all channels with the given name.
     */
    default List<ServerChannel> getChannelsByNameIgnoreCase(String name) {
        return getChannels().stream()
                .filter(channel -> channel.getName().equalsIgnoreCase(name))
                .collect(Collectors.toList());
    }

    /**
     * Gets a channel category by it's id.
     *
     * @param id The id of the channel category.
     * @return The channel category with the given id.
     */
    default Optional<ChannelCategory> getChannelCategoryById(long id) {
        return getChannelById(id)
                .filter(channel -> channel instanceof ChannelCategory)
                .map(channel -> (ChannelCategory) channel);
    }

    /**
     * Gets a channel category by it's id.
     *
     * @param id The id of the channel category.
     * @return The channel category with the given id.
     */
    default Optional<ChannelCategory> getChannelCategoryById(String id) {
        try {
            return getChannelCategoryById(Long.valueOf(id));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Gets a sorted list (by position) with all channel categories with the given name.
     * This method is case sensitive!
     *
     * @param name The name of the channel categories.
     * @return A sorted list (by position) with all channel categories with the given name.
     */
    default List<ChannelCategory> getChannelCategoriesByName(String name) {
        return getChannelCategories().stream()
                .filter(channel -> channel.getName().equals(name))
                .collect(Collectors.toList());
    }

    /**
     * Gets a sorted list (by position) with all channel categories with the given name.
     * This method is case insensitive!
     *
     * @param name The name of the channel categories.
     * @return A sorted list (by position) with all channel categories with the given name.
     */
    default List<ChannelCategory> getChannelCategoriesByNameIgnoreCase(String name) {
        return getChannelCategories().stream()
                .filter(channel -> channel.getName().equalsIgnoreCase(name))
                .collect(Collectors.toList());
    }

    /**
     * Gets a text channel by it's id.
     *
     * @param id The id of the text channel.
     * @return The text channel with the given id.
     */
    default Optional<ServerTextChannel> getTextChannelById(long id) {
        return getChannelById(id)
                .filter(channel -> channel instanceof ServerTextChannel)
                .map(channel -> (ServerTextChannel) channel);
    }

    /**
     * Gets a text channel by it's id.
     *
     * @param id The id of the text channel.
     * @return The text channel with the given id.
     */
    default Optional<ServerTextChannel> getTextChannelById(String id) {
        try {
            return getTextChannelById(Long.valueOf(id));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Gets a sorted list (by position) with all text channels with the given name.
     * This method is case sensitive!
     *
     * @param name The name of the text channels.
     * @return A sorted list (by position) with all text channels with the given name.
     */
    default List<ServerTextChannel> getTextChannelsByName(String name) {
        return getTextChannels().stream()
                .filter(channel -> channel.getName().equals(name))
                .collect(Collectors.toList());
    }

    /**
     * Gets a sorted list (by position) with all text channels with the given name.
     * This method is case insensitive!
     *
     * @param name The name of the text channels.
     * @return A sorted list (by position) with all text channels with the given name.
     */
    default List<ServerTextChannel> getTextChannelsByNameIgnoreCase(String name) {
        return getTextChannels().stream()
                .filter(channel -> channel.getName().equalsIgnoreCase(name))
                .collect(Collectors.toList());
    }

    /**
     * Gets a voice channel by it's id.
     *
     * @param id The id of the voice channel.
     * @return The voice channel with the given id.
     */
    default Optional<ServerVoiceChannel> getVoiceChannelById(long id) {
        return getChannelById(id)
                .filter(channel -> channel instanceof ServerVoiceChannel)
                .map(channel -> (ServerVoiceChannel) channel);
    }

    /**
     * Gets a voice channel by it's id.
     *
     * @param id The id of the voice channel.
     * @return The voice channel with the given id.
     */
    default Optional<ServerVoiceChannel> getVoiceChannelById(String id) {
        try {
            return getVoiceChannelById(Long.valueOf(id));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Gets a sorted list (by position) with all voice channels with the given name.
     * This method is case sensitive!
     *
     * @param name The name of the voice channels.
     * @return A sorted list (by position) with all voice channels with the given name.
     */
    default List<ServerVoiceChannel> getVoiceChannelsByName(String name) {
        return getVoiceChannels().stream()
                .filter(channel -> channel.getName().equals(name))
                .collect(Collectors.toList());
    }

    /**
     * Gets a sorted list (by position) with all voice channels with the given name.
     * This method is case insensitive!
     *
     * @param name The name of the voice channels.
     * @return A sorted list (by position) with all voice channels with the given name.
     */
    default List<ServerVoiceChannel> getVoiceChannelsByNameIgnoreCase(String name) {
        return getVoiceChannels().stream()
                .filter(channel -> channel.getName().equalsIgnoreCase(name))
                .collect(Collectors.toList());
    }

    /**
     * Adds a listener, which listens to message creates in this server.
     *
     * @param listener The listener to add.
     */
    default void addMessageCreateListener(MessageCreateListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), MessageCreateListener.class, listener);
    }

    /**
     * Gets a list with all registered message create listeners.
     *
     * @return A list with all registered message create listeners.
     */
    default List<MessageCreateListener> getMessageCreateListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), MessageCreateListener.class);
    }

    /**
     * Adds a listener, which listens to you leaving this server.
     *
     * @param listener The listener to add.
     */
    default void addServerLeaveListener(ServerLeaveListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), ServerLeaveListener.class, listener);
    }

    /**
     * Gets a list with all registered server leaves listeners.
     *
     * @return A list with all registered server leaves listeners.
     */
    default List<ServerLeaveListener> getServerLeaveListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), ServerLeaveListener.class);
    }

    /**
     * Adds a listener, which listens to this server becoming unavailable.
     *
     * @param listener The listener to add.
     */
    default void addServerBecomesUnavailableListener(ServerBecomesUnavailableListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(
                Server.class, getId(), ServerBecomesUnavailableListener.class, listener);
    }

    /**
     * Gets a list with all registered server becomes unavailable listeners.
     *
     * @return A list with all registered server becomes unavailable listeners.
     */
    default List<ServerBecomesUnavailableListener> getServerBecomesUnavailableListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(
                Server.class, getId(), ServerBecomesUnavailableListener.class);
    }

    /**
     * Adds a listener, which listens to users starting to type in this server.
     *
     * @param listener The listener to add.
     */
    default void addUserStartTypingListener(UserStartTypingListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), UserStartTypingListener.class, listener);
    }

    /**
     * Gets a list with all registered user starts typing listeners.
     *
     * @return A list with all registered user starts typing listeners.
     */
    default List<UserStartTypingListener> getUserStartTypingListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), UserStartTypingListener.class);
    }

    /**
     * Adds a listener, which listens to server channel creations in this server.
     *
     * @param listener The listener to add.
     */
    default void addServerChannelCreateListener(ServerChannelCreateListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(
                Server.class, getId(), ServerChannelCreateListener.class, listener);
    }

    /**
     * Gets a list with all registered server channel create listeners.
     *
     * @return A list with all registered server channel create listeners.
     */
    default List<ServerChannelCreateListener> getServerChannelCreateListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), ServerChannelCreateListener.class);
    }

    /**
     * Adds a listener, which listens to server channel deletions in this server.
     *
     * @param listener The listener to add.
     */
    default void addServerChannelDeleteListener(ServerChannelDeleteListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(
                Server.class, getId(), ServerChannelDeleteListener.class, listener);
    }

    /**
     * Gets a list with all registered server channel delete listeners.
     *
     * @return A list with all registered server channel delete listeners.
     */
    default List<ServerChannelDeleteListener> getServerChannelDeleteListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), ServerChannelDeleteListener.class);
    }

    /**
     * Adds a listener, which listens to message deletions in this server.
     *
     * @param listener The listener to add.
     */
    default void addMessageDeleteListener(MessageDeleteListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), MessageDeleteListener.class, listener);
    }

    /**
     * Gets a list with all registered message delete listeners.
     *
     * @return A list with all registered message delete listeners.
     */
    default List<MessageDeleteListener> getMessageDeleteListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), MessageDeleteListener.class);
    }

    /**
     * Adds a listener, which listens to message edits in this server.
     *
     * @param listener The listener to add.
     */
    default void addMessageEditListener(MessageEditListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), MessageEditListener.class, listener);
    }

    /**
     * Gets a list with all registered message edit listeners.
     *
     * @return A list with all registered message edit listeners.
     */
    default List<MessageEditListener> getMessageEditListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), MessageEditListener.class);
    }

    /**
     * Adds a listener, which listens to reactions being added on this server.
     *
     * @param listener The listener to add.
     */
    default void addReactionAddListener(ReactionAddListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), ReactionAddListener.class, listener);
    }

    /**
     * Gets a list with all registered reaction add listeners.
     *
     * @return A list with all registered reaction add listeners.
     */
    default List<ReactionAddListener> getReactionAddListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), ReactionAddListener.class);
    }

    /**
     * Adds a listener, which listens to reactions being removed on this server.
     *
     * @param listener The listener to add.
     */
    default void addReactionRemoveListener(ReactionRemoveListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), ReactionRemoveListener.class, listener);
    }

    /**
     * Gets a list with all registered reaction remove listeners.
     *
     * @return A list with all registered reaction remove listeners.
     */
    default List<ReactionRemoveListener> getReactionRemoveListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), ReactionRemoveListener.class);
    }

    /**
     * Adds a listener, which listens to users joining this server.
     *
     * @param listener The listener to add.
     */
    default void addServerMemberAddListener(ServerMemberAddListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), ServerMemberAddListener.class, listener);
    }

    /**
     * Gets a list with all registered server member add listeners.
     *
     * @return A list with all registered server member add listeners.
     */
    default List<ServerMemberAddListener> getServerMemberAddListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), ServerMemberAddListener.class);
    }

    /**
     * Adds a listener, which listens to users leaving this server.
     *
     * @param listener The listener to add.
     */
    default void addServerMemberRemoveListener(ServerMemberRemoveListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(
                Server.class, getId(), ServerMemberRemoveListener.class, listener);
    }

    /**
     * Gets a list with all registered server member remove listeners.
     *
     * @return A list with all registered server member remove listeners.
     */
    default List<ServerMemberRemoveListener> getServerMemberRemoveListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), ServerMemberRemoveListener.class);
    }

    /**
     * Adds a listener, which listens to server name changes.
     *
     * @param listener The listener to add.
     */
    default void addServerChangeNameListener(ServerChangeNameListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), ServerChangeNameListener.class, listener);
    }

    /**
     * Gets a list with all registered server change name listeners.
     *
     * @return A list with all registered server change name listeners.
     */
    default List<ServerChangeNameListener> getServerChangeNameListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), ServerChangeNameListener.class);
    }

    /**
     * Adds a listener, which listens to server channel name changes in this server.
     *
     * @param listener The listener to add.
     */
    default void addServerChannelChangeNameListener(ServerChannelChangeNameListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(
                Server.class, getId(), ServerChannelChangeNameListener.class, listener);
    }

    /**
     * Gets a list with all registered server channel change name listeners.
     *
     * @return A list with all registered server channel change name listeners.
     */
    default List<ServerChannelChangeNameListener> getServerChannelChangeNameListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(
                Server.class, getId(), ServerChannelChangeNameListener.class);
    }

    /**
     * Adds a listener, which listens to server channel position changes in this server.
     *
     * @param listener The listener to add.
     */
    default void addServerChannelChangePositionListener(ServerChannelChangePositionListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(
                Server.class, getId(), ServerChannelChangePositionListener.class, listener);
    }

    /**
     * Gets a list with all registered server channel change position listeners.
     *
     * @return A list with all registered server channel change position listeners.
     */
    default List<ServerChannelChangePositionListener> getServerChannelChangePositionListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(
                Server.class, getId(), ServerChannelChangePositionListener.class);
    }

    /**
     * Adds a listener, which listens to custom emoji creations in this server.
     *
     * @param listener The listener to add.
     */
    default void addCustomEmojiCreateListener(CustomEmojiCreateListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), CustomEmojiCreateListener.class, listener);
    }

    /**
     * Gets a list with all registered custom emoji create listeners.
     *
     * @return A list with all registered custom emoji create listeners.
     */
    default List<CustomEmojiCreateListener> getCustomEmojiCreateListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), CustomEmojiCreateListener.class);
    }

    /**
     * Adds a listener, which listens to game changes of users in this server.
     *
     * @param listener The listener to add.
     */
    default void addUserChangeGameListener(UserChangeGameListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), UserChangeGameListener.class, listener);
    }

    /**
     * Gets a list with all registered user change game listeners.
     *
     * @return A list with all registered custom emoji create listeners.
     */
    default List<UserChangeGameListener> getUserChangeGameListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), UserChangeGameListener.class);
    }

    /**
     * Adds a listener, which listens to status changes of users in this server.
     *
     * @param listener The listener to add.
     */
    default void addUserChangeStatusListener(UserChangeStatusListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), UserChangeStatusListener.class, listener);
    }

    /**
     * Gets a list with all registered user change status listeners.
     *
     * @return A list with all registered custom emoji create listeners.
     */
    default List<UserChangeStatusListener> getUserChangeStatusListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), UserChangeStatusListener.class);
    }

    /**
     * Adds a listener, which listens to role permission changes in this server.
     *
     * @param listener The listener to add.
     */
    default void addRoleChangePermissionsListener(RoleChangePermissionsListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(
                Server.class, getId(), RoleChangePermissionsListener.class, listener);
    }

    /**
     * Gets a list with all registered role change permissions listeners.
     *
     * @return A list with all registered role change permissions listeners.
     */
    default List<RoleChangePermissionsListener> getRoleChangePermissionsListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(
                Server.class, getId(), RoleChangePermissionsListener.class);
    }

    /**
     * Adds a listener, which listens to role position changes in this server.
     *
     * @param listener The listener to add.
     */
    default void addRoleChangePositionListener(RoleChangePositionListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(
                Server.class, getId(), RoleChangePositionListener.class, listener);
    }

    /**
     * Gets a list with all registered role change position listeners.
     *
     * @return A list with all registered role change position listeners.
     */
    default List<RoleChangePositionListener> getRoleChangePositionListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), RoleChangePositionListener.class);
    }

    /**
     * Adds a listener, which listens to overwritten permission changes in this server.
     *
     * @param listener The listener to add.
     */
    default void addServerChannelChangeOverwrittenPermissionsListener(
            ServerChannelChangeOverwrittenPermissionsListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(
                Server.class, getId(), ServerChannelChangeOverwrittenPermissionsListener.class, listener);
    }

    /**
     * Gets a list with all registered server channel change overwritten permissions listeners.
     *
     * @return A list with all registered server channel change overwritten permissions listeners.
     */
    default List<ServerChannelChangeOverwrittenPermissionsListener>
            getServerChannelChangeOverwrittenPermissionsListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(
                Server.class, getId(), ServerChannelChangeOverwrittenPermissionsListener.class);
    }

    /**
     * Adds a listener, which listens to role creations in this server.
     *
     * @param listener The listener to add.
     */
    default void addRoleCreateListener(RoleCreateListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), RoleCreateListener.class, listener);
    }

    /**
     * Gets a list with all registered role create listeners.
     *
     * @return A list with all registered role create listeners.
     */
    default List<RoleCreateListener> getRoleCreateListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), RoleCreateListener.class);
    }

    /**
     * Adds a listener, which listens to role deletions in this server.
     *
     * @param listener The listener to add.
     */
    default void addRoleDeleteListener(RoleDeleteListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(Server.class, getId(), RoleDeleteListener.class, listener);
    }

    /**
     * Gets a list with all registered role delete listeners.
     *
     * @return A list with all registered role delete listeners.
     */
    default List<RoleDeleteListener> getRoleDeleteListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), RoleDeleteListener.class);
    }

    /**
     * Adds a listener, which listens to user nickname changes in this server.
     *
     * @param listener The listener to add.
     */
    default void addUserChangeNicknameListener(UserChangeNicknameListener listener) {
        ((ImplDiscordApi) getApi()).addObjectListener(
                Server.class, getId(), UserChangeNicknameListener.class, listener);
    }

    /**
     * Gets a list with all registered user change nickname listeners.
     *
     * @return A list with all registered user change nickname listeners.
     */
    default List<UserChangeNicknameListener> getUserChangeNicknameListeners() {
        return ((ImplDiscordApi) getApi()).getObjectListeners(Server.class, getId(), UserChangeNicknameListener.class);
    }

}
