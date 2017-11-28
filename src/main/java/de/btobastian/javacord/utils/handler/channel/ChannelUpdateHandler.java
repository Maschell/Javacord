package de.btobastian.javacord.utils.handler.channel;

import de.btobastian.javacord.DiscordApi;
import de.btobastian.javacord.entities.DiscordEntity;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.channels.ServerChannel;
import de.btobastian.javacord.entities.channels.impl.ImplChannelCategory;
import de.btobastian.javacord.entities.channels.impl.ImplServerTextChannel;
import de.btobastian.javacord.entities.channels.impl.ImplServerVoiceChannel;
import de.btobastian.javacord.entities.permissions.Permissions;
import de.btobastian.javacord.entities.permissions.Role;
import de.btobastian.javacord.entities.permissions.impl.ImplPermissions;
import de.btobastian.javacord.events.server.channel.ServerChannelChangeNameEvent;
import de.btobastian.javacord.events.server.channel.ServerChannelChangeOverwrittenPermissionsEvent;
import de.btobastian.javacord.events.server.channel.ServerChannelChangePositionEvent;
import de.btobastian.javacord.events.server.channel.ServerTextChannelChangeTopicEvent;
import de.btobastian.javacord.listeners.server.channel.ServerChannelChangeNameListener;
import de.btobastian.javacord.listeners.server.channel.ServerChannelChangeOverwrittenPermissionsListener;
import de.btobastian.javacord.listeners.server.channel.ServerChannelChangePositionListener;
import de.btobastian.javacord.listeners.server.channel.ServerTextChannelChangeTopicListener;
import de.btobastian.javacord.utils.PacketHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the channel update packet.
 */
public class ChannelUpdateHandler extends PacketHandler {

    /**
     * Creates a new instance of this class.
     *
     * @param api The api.
     */
    public ChannelUpdateHandler(DiscordApi api) {
        super(api, true, "CHANNEL_UPDATE");
    }

    @Override
    public void handle(JSONObject packet) {
        int type = packet.getInt("type");
        switch (type) {
            case 0:
                handleServerChannel(packet);
                handleServerTextChannel(packet);
                break;
            case 1:
                handlePrivateChannel(packet);
                break;
            case 2:
                handleServerChannel(packet);
                handleServerVoiceChannel(packet);
                break;
            case 4:
                handleServerChannel(packet);
                handleChannelCategory(packet);
                break;
        }
    }

    /**
     * Handles a server channel update.
     *
     * @param channel The channel data.
     */
    private void handleServerChannel(JSONObject channel) {
        long channelId = Long.parseLong(channel.getString("id"));
        api.getServerChannelById(channelId).ifPresent(c -> {
            String oldName = c.getName();
            String newName = channel.getString("name");
            if (!Objects.deepEquals(oldName, newName)) {
                c.asChannelCategory().ifPresent(cc -> ((ImplChannelCategory) cc).setName(newName));
                c.asServerTextChannel().ifPresent(stc -> ((ImplServerTextChannel) stc).setName(newName));
                c.asServerVoiceChannel().ifPresent(svc -> ((ImplServerVoiceChannel) svc).setName(newName));
                ServerChannelChangeNameEvent event =
                        new ServerChannelChangeNameEvent(c, newName, oldName);

                List<ServerChannelChangeNameListener> listeners = new ArrayList<>();
                listeners.addAll(c.getServerChannelChangeNameListeners());
                listeners.addAll(c.getServer().getServerChannelChangeNameListeners());
                listeners.addAll(api.getServerChannelChangeNameListeners());

                dispatchEvent(listeners, listener -> listener.onServerChannelChangeName(event));
            }

            int oldPosition = c.getRawPosition();
            int newPosition = channel.getInt("position");
            if (oldPosition != newPosition) {
                c.asChannelCategory().ifPresent(cc -> ((ImplChannelCategory) cc).setPosition(newPosition));
                c.asServerTextChannel().ifPresent(stc -> ((ImplServerTextChannel) stc).setPosition(newPosition));
                c.asServerVoiceChannel().ifPresent(svc -> ((ImplServerVoiceChannel) svc).setPosition(newPosition));

                ServerChannelChangePositionEvent event =
                        new ServerChannelChangePositionEvent(c, newPosition, oldPosition);

                List<ServerChannelChangePositionListener> listeners = new ArrayList<>();
                listeners.addAll(c.getServerChannelChangePositionListeners());
                listeners.addAll(c.getServer().getServerChannelChangePositionListeners());
                listeners.addAll(api.getServerChannelChangePositionListeners());

                dispatchEvent(listeners, listener -> listener.onServerChannelChangePosition(event));
            }

            JSONArray permissionOverwritesJson = channel.has("permission_overwrites") ?
                    channel.getJSONArray("permission_overwrites") : new JSONArray();
            Collection<Long> rolesWithOverwrittenPermissions = new HashSet<>();
            Collection<Long> usersWithOverwrittenPermissions = new HashSet<>();
            for (int i = 0; i < permissionOverwritesJson.length(); i++) {
                JSONObject permissionOverwriteJson = permissionOverwritesJson.getJSONObject(i);
                Permissions oldOverwrittenPermissions = null;
                DiscordEntity entity = null;
                ConcurrentHashMap<Long, Permissions> overwrittenPermissions = null;
                switch (permissionOverwriteJson.getString("type")) {
                    case "role":
                        entity = api.getRoleById(permissionOverwriteJson.getString("id")).orElseThrow(() ->
                                new IllegalStateException("Received channel update event with unknown role!"));
                        oldOverwrittenPermissions = c.getOverwrittenPermissions((Role) entity);
                        if (c instanceof ImplChannelCategory) {
                            overwrittenPermissions = ((ImplChannelCategory) c).getOverwrittenRolePermissions();
                        } else if (c instanceof ImplServerTextChannel) {
                            overwrittenPermissions = ((ImplServerTextChannel) c).getOverwrittenRolePermissions();
                        } else if (c instanceof ImplServerVoiceChannel) {
                            overwrittenPermissions = ((ImplServerVoiceChannel) c).getOverwrittenRolePermissions();
                        }
                        rolesWithOverwrittenPermissions.add(entity.getId());
                        break;
                    case "member":
                        entity = api.getUserById(permissionOverwriteJson.getString("id")).orElseThrow(() ->
                                new IllegalStateException("Received channel update event with unknown user!"));
                        oldOverwrittenPermissions = c.getOverwrittenPermissions((User) entity);
                        if (c instanceof ImplChannelCategory) {
                            overwrittenPermissions = ((ImplChannelCategory) c).getOverwrittenUserPermissions();
                        } else if (c instanceof ImplServerTextChannel) {
                            overwrittenPermissions = ((ImplServerTextChannel) c).getOverwrittenUserPermissions();
                        } else if (c instanceof ImplServerVoiceChannel) {
                            overwrittenPermissions = ((ImplServerVoiceChannel) c).getOverwrittenUserPermissions();
                        }
                        usersWithOverwrittenPermissions.add(entity.getId());
                        break;
                    default:
                        throw new IllegalStateException(
                                "Permission overwrite object with unknown type: " + permissionOverwriteJson.toString());
                }
                int allow = permissionOverwriteJson.optInt("allow", 0);
                int deny = permissionOverwriteJson.optInt("deny", 0);
                Permissions newOverwrittenPermissions = new ImplPermissions(allow, deny);
                if (!newOverwrittenPermissions.equals(oldOverwrittenPermissions)) {
                    overwrittenPermissions.put(entity.getId(), newOverwrittenPermissions);
                    dispatchServerChannelChangeOverwrittenPermissionsEvent(
                            c, newOverwrittenPermissions, oldOverwrittenPermissions, entity);
                }
            }
            ConcurrentHashMap<Long, Permissions> overwrittenRolePermissions = null;
            ConcurrentHashMap<Long, Permissions> overwrittenUserPermissions = null;
            if (c instanceof ImplChannelCategory) {
                overwrittenRolePermissions = ((ImplChannelCategory) c).getOverwrittenRolePermissions();
                overwrittenUserPermissions = ((ImplChannelCategory) c).getOverwrittenUserPermissions();
            } else if (c instanceof ImplServerTextChannel) {
                overwrittenRolePermissions = ((ImplServerTextChannel) c).getOverwrittenRolePermissions();
                overwrittenUserPermissions = ((ImplServerTextChannel) c).getOverwrittenUserPermissions();
            } else if (c instanceof ImplServerVoiceChannel) {
                overwrittenRolePermissions = ((ImplServerVoiceChannel) c).getOverwrittenRolePermissions();
                overwrittenUserPermissions = ((ImplServerVoiceChannel) c).getOverwrittenUserPermissions();
            }

            Iterator<Map.Entry<Long, Permissions>> userIt = overwrittenUserPermissions.entrySet().iterator();
            while (userIt.hasNext()) {
                Map.Entry<Long, Permissions> entry = userIt.next();
                if (usersWithOverwrittenPermissions.contains(entry.getKey())) {
                    continue;
                }
                api.getUserById(entry.getKey()).ifPresent(user -> {
                    Permissions oldPermissions = entry.getValue();
                    userIt.remove();
                    dispatchServerChannelChangeOverwrittenPermissionsEvent(
                            c, ImplPermissions.EMPTY_PERMISSIONS, oldPermissions, user);
                });
            }

            Iterator<Map.Entry<Long, Permissions>> roleIt = overwrittenRolePermissions.entrySet().iterator();
            while (roleIt.hasNext()) {
                Map.Entry<Long, Permissions> entry = roleIt.next();
                if (rolesWithOverwrittenPermissions.contains(entry.getKey())) {
                    continue;
                }
                api.getRoleById(entry.getKey()).ifPresent(role -> {
                    Permissions oldPermissions = entry.getValue();
                    roleIt.remove();
                    dispatchServerChannelChangeOverwrittenPermissionsEvent(
                            c, ImplPermissions.EMPTY_PERMISSIONS, oldPermissions, role);
                });
            }
        });
    }

    /**
     * Handles a channel category update.
     *
     * @param channel The channel data.
     */
    private void handleChannelCategory(JSONObject channel) {
        long channelId = Long.parseLong(channel.getString("id"));
        api.getChannelCategoryById(channelId).map(c -> ((ImplChannelCategory) c)).ifPresent(c -> {
            String oldName = c.getName();
            String newName = channel.getString("name");
            if (!Objects.deepEquals(oldName, newName)) {
                c.setName(newName);
                ServerChannelChangeNameEvent event =
                        new ServerChannelChangeNameEvent(c, newName, oldName);

                List<ServerChannelChangeNameListener> listeners = new ArrayList<>();
                listeners.addAll(c.getServerChannelChangeNameListeners());
                listeners.addAll(c.getServer().getServerChannelChangeNameListeners());
                listeners.addAll(api.getServerChannelChangeNameListeners());

                dispatchEvent(listeners, listener -> listener.onServerChannelChangeName(event));
            }
        });
    }

    /**
     * Handles a server text channel update.
     *
     * @param jsonChannel The json channel data.
     */
    private void handleServerTextChannel(JSONObject jsonChannel) {
        long channelId = Long.parseLong(jsonChannel.getString("id"));
        api.getTextChannelById(channelId).map(c -> ((ImplServerTextChannel) c)).ifPresent(channel -> {
            String oldTopic = channel.getTopic();
            String newTopic =
                    jsonChannel.has("topic") && !jsonChannel.isNull("topic") ? jsonChannel.getString("topic") : "";
            if (!oldTopic.equals(newTopic)) {
                channel.setTopic(newTopic);

                ServerTextChannelChangeTopicEvent event =
                        new ServerTextChannelChangeTopicEvent(channel, newTopic, oldTopic);

                List<ServerTextChannelChangeTopicListener> listeners = new ArrayList<>();
                listeners.addAll(channel.getServerTextChannelChangeTopicListeners());
                listeners.addAll(channel.getServer().getServerTextChannelChangeTopicListeners());
                listeners.addAll(api.getServerTextChannelChangeTopicListeners());

                dispatchEvent(listeners, listener -> listener.onServerTextChannelChangeTopic(event));
            }
        });
    }

    /**
     * Handles a server voice channel update.
     *
     * @param channel The channel data.
     */
    private void handleServerVoiceChannel(JSONObject channel) {
        long serverId = Long.parseLong(channel.getString("guild_id"));

    }

    /**
     * Handles a private channel update.
     *
     * @param channel The channel data.
     */
    private void handlePrivateChannel(JSONObject channel) {

    }

    /**
     * Dispatches a ServerChannelChangeOverwrittenPermissionsEvent.
     *
     * @param channel The channel of the event.
     * @param newPermissions The new overwritten permissions.
     * @param oldPermissions The old overwritten permissions.
     * @param entity The entity of the event.
     */
    private void dispatchServerChannelChangeOverwrittenPermissionsEvent(
            ServerChannel channel, Permissions newPermissions, Permissions oldPermissions, DiscordEntity entity) {
        if (newPermissions.equals(oldPermissions)) {
            // This can be caused by adding a user/role in a channels overwritten permissions without modifying
            // any of it's values. We don't need to dispatch an event for this.
            return;
        }
        ServerChannelChangeOverwrittenPermissionsEvent event =
                new ServerChannelChangeOverwrittenPermissionsEvent(
                        channel, newPermissions, oldPermissions, entity);

        List<ServerChannelChangeOverwrittenPermissionsListener> listeners = new ArrayList<>();
        if (entity instanceof User) {
            listeners.addAll(((User) entity).getServerChannelChangeOverwrittenPermissionsListeners());
        }
        if (entity instanceof Role) {
            listeners.addAll(((Role) entity).getServerChannelChangeOverwrittenPermissionsListeners());
        }
        listeners.addAll(channel.getServerChannelChangeOverwrittenPermissionsListeners());
        listeners.addAll(channel.getServer().getServerChannelChangeOverwrittenPermissionsListeners());
        listeners.addAll(api.getServerChannelChangeOverwrittenPermissionsListeners());

        dispatchEvent(listeners, listener -> listener.onServerChannelChangeOverwrittenPermissions(event));
    }

}