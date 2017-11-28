package de.btobastian.javacord.entities.channels;

/**
 * A enum with all different channel types.
 */
public enum ChannelType {

    SERVER_TEXT_CHANNEL(0),
    PRIVATE_CHANNEL(1),
    SERVER_VOICE_CHANNEL(2),
    GROUP_CHANNEL(3),
    CHANNEL_CATEGORY(4),
    UNKNOWN(-1);

    /**
     * The id of the channel type.
     */
    private final int id;

    /**
     * Creates a new channel type.
     *
     * @param id The id of the channel type.
     */
    ChannelType(int id) {
        this.id = id;
    }

    /**
     * Gets the id of the channel type.
     *
     * @return The id of the channel type.
     */
    public int getId() {
        return id;
    }

    /**
     * Gets a channel type by it's id.
     *
     * @param id The id of the channel type.
     * @return The channel type with the given id.
     */
    public static ChannelType fromId(int id) {
        for (ChannelType type : values()) {
            if (type.getId() == id) {
                return type;
            }
        }
        return UNKNOWN;
    }

}
