/*
 * Copyright (C) 2017 Bastian Oppermann
 * 
 * This file is part of Javacord.
 * 
 * Javacord is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser general Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 * 
 * Javacord is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, see <http://www.gnu.org/licenses/>.
 */
package de.btobastian.javacord.utils.handler.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import de.btobastian.javacord.DiscordApi;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.impl.ImplServer;
import de.btobastian.javacord.entities.permissions.Role;
import de.btobastian.javacord.entities.permissions.impl.ImplRole;
import de.btobastian.javacord.events.server.role.UserRoleAddEvent;
import de.btobastian.javacord.events.server.role.UserRoleRemoveEvent;
import de.btobastian.javacord.events.user.UserChangeNicknameEvent;
import de.btobastian.javacord.listeners.server.role.UserRoleAddListener;
import de.btobastian.javacord.listeners.server.role.UserRoleRemoveListener;
import de.btobastian.javacord.listeners.user.UserChangeNicknameListener;
import de.btobastian.javacord.utils.PacketHandler;
import de.btobastian.javacord.utils.logging.LoggerUtil;

/**
 * Handles the guild member update packet.
 */
public class GuildMemberUpdateHandler extends PacketHandler {

    private static final Logger logger = LoggerUtil.getLogger(GuildMemberUpdateHandler.class);

    /**
     * Creates a new instance of this class.
     *
     * @param api
     *            The api.
     */
    public GuildMemberUpdateHandler(DiscordApi api) {
        super(api, true, "GUILD_MEMBER_UPDATE");
    }

    @Override
    public void handle(JSONObject packet) {
        api.getServerById(packet.getString("guild_id")).map(server -> (ImplServer) server).ifPresent(server -> {
            User user = api.getOrCreateUser(packet.getJSONObject("user"));

            if (packet.has("nick")) {
                String newNickname = packet.optString("nick", null);
                String oldNickname = server.getNickname(user).orElse(null);
                if (!Objects.deepEquals(newNickname, oldNickname)) {
                    server.setNickname(user, newNickname);

                    UserChangeNicknameEvent event = new UserChangeNicknameEvent(api, user, server, newNickname, oldNickname);

                    List<UserChangeNicknameListener> listeners = new ArrayList<>();
                    listeners.addAll(user.getUserChangeNicknameListeners());
                    listeners.addAll(server.getUserChangeNicknameListeners());
                    listeners.addAll(api.getUserChangeNicknameListeners());

                    dispatchEvent(listeners, listener -> listener.onUserChangeNickname(event));
                }
            }
            // get array with all roles
            JSONArray jsonRoles = packet.getJSONArray("roles");
            Role[] roles = new Role[jsonRoles.length()];
            for (int i = 0; i < jsonRoles.length(); i++) {
                roles[i] = server.getRoleById(jsonRoles.getString(i)).get();
            }
            
            // iterate throw all current roles and remove roles which aren't in the roles array
            for (final Role role : user.getRoles(server)) {
                if (role.getName().equals("@everyone")) continue;
                boolean contains = false;
                for (Role r : roles) {
                    if (role == r) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    ((ImplRole) role).removeUserFromCache(user);
                    UserRoleRemoveEvent event = new UserRoleRemoveEvent(api, role, user);
                    List<UserRoleRemoveListener> listeners = api.getUserRoleRemoveListener();
                    dispatchEvent(listeners, listener -> listener.onUserRoleRemove(event));
                }
            }

            // iterate through all roles of the roles array and add roles which aren't in the current roles list
            for (final Role role : roles) {
                if (!user.getRoles(server).contains(role)) {
                    ((ImplRole) role).addUserToCache(user);
                    UserRoleAddEvent event = new UserRoleAddEvent(api, role, user);
                    List<UserRoleAddListener> listeners = api.getUserRoleAddListener();
                    dispatchEvent(listeners, listener -> listener.onUserRoleAdd(event));

                }
            }
        });
    }

}