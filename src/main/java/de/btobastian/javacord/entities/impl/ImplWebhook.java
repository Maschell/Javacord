package de.btobastian.javacord.entities.impl;

import de.btobastian.javacord.DiscordApi;
import de.btobastian.javacord.ImplDiscordApi;
import de.btobastian.javacord.entities.Icon;
import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.Webhook;
import de.btobastian.javacord.entities.channels.TextChannel;
import de.btobastian.javacord.utils.logging.LoggerUtil;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

/**
 * The implementation of {@link Webhook}.
 */
public class ImplWebhook implements Webhook {

    /**
     * The logger of this class.
     */
    private static final Logger logger = LoggerUtil.getLogger(ImplWebhook.class);

    private final ImplDiscordApi api;

    private final long id;
    private final Long serverId;
    private final long channelId;
    private final User user;
    private final String name;
    private final String avatarId;
    private final String token;

    /**
     * Creates a new webhook.
     *
     * @param api The discord api instance.
     * @param data The json data of the webhook.
     */
    public ImplWebhook(DiscordApi api, JSONObject data) {
        this.api = (ImplDiscordApi) api;

        this.id = Long.parseLong(data.getString("id"));
        this.serverId = data.has("guild_id") ? Long.parseLong(data.getString("guild_id")) : null;
        this.channelId = Long.parseLong(data.getString("channel_id"));
        this.user = data.has("user") ? this.api.getOrCreateUser(data.getJSONObject("user")) : null;
        this.name = data.has("name") && !data.isNull("name") ? data.getString("name") : null;
        this.avatarId = data.has("avatar") && !data.isNull("avatar") ? data.getString("avatar") : null;
        this.token = data.getString("token");
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
    public Optional<Long> getServerId() {
        return Optional.ofNullable(serverId);
    }

    @Override
    public Optional<Server> getServer() {
        return getServerId().map(api::getServerById).filter(Optional::isPresent).map(Optional::get);
    }

    @Override
    public Optional<TextChannel> getChannel() {
        return api.getTextChannelById(getChannelId());
    }

    @Override
    public long getChannelId() {
        return channelId;
    }

    @Override
    public Optional<User> getCreator() {
        return Optional.ofNullable(user);
    }

    @Override
    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    @Override
    public Optional<Icon> getAvatar() {
        if (avatarId != null) {
            String url = "https://cdn.discordapp.com/avatars/" + getId() + "/" + avatarId +
                    (avatarId.startsWith("a_") ? ".gif" : ".png");
            try {
                return Optional.of(new ImplIcon(getApi(), new URL(url)));
            } catch (MalformedURLException e) {
                logger.warn("Seems like the url of the avatar is malformed! Please contact the developer!", e);
            }
        }
        return Optional.empty();
    }

    @Override
    public String getToken() {
        return token;
    }
}
