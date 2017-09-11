package custodian

import custodian.resource.FileManager
import custodian.resource.TemplateManager

import ch.qos.logback.classic.Level

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import sx.blah.discord.Discord4J
import sx.blah.discord.api.ClientBuilder
import sx.blah.discord.api.IDiscordClient
import sx.blah.discord.api.events.EventSubscriber
import sx.blah.discord.handle.impl.events.guild.*
import sx.blah.discord.handle.impl.events.guild.channel.*
import sx.blah.discord.handle.impl.events.guild.channel.message.*
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.*
import sx.blah.discord.handle.impl.events.guild.member.*
import sx.blah.discord.handle.impl.events.guild.role.*
import sx.blah.discord.handle.impl.events.guild.user.*
import sx.blah.discord.handle.impl.events.guild.voice.*
import sx.blah.discord.handle.impl.events.guild.voice.user.*
import sx.blah.discord.handle.impl.events.module.*
import sx.blah.discord.handle.impl.events.shard.*
import sx.blah.discord.handle.impl.events.user.*
import sx.blah.discord.util.DiscordException
import sx.blah.discord.util.EmbedBuilder
import sx.blah.discord.util.Image
import sx.blah.discord.util.MessageBuilder

class Custodian {

    public static final Logger LOGGER = LoggerFactory.getLogger("Custodian") // Default logging level is debug.

    Image avatar
    String username
    String status
    String playing
    String streamUrl

    long ownerId
    String logLevel
    String tokenFile
    String googleOAuthFile

    String trigger

    IDiscordClient client
    Map servers // Map is from id to Server object

    Custodian() {
        Map config = getConfig()
        this.client = this.makeClient(FileManager.read(config.tokenFile).trim())
        this.client.login()

        // Load bot-wide data from configuration file.
        this.avatar = loadAvatarFromLocalFile(config.avatar) ?: /*loadAvatarFromURL(config.avatar) */?: Image.defaultAvatar()
        this.username = config.username ?: client.ourUser.name
        this.status = config.status ?: 'online'
        this.playing = config.playing ?: null
        this.streamUrl = config.streamUrl ?: ''

        this.ownerId = config.ownerId ?: this.client.applicationOwner.id ?: -1
        this.logLevel = config.logLevel ?: 'warn'
        this.setLogLevel(this.logLevel)
        this.tokenFile = config.tokenFile
        this.googleOAuthFile = config.googleOAuthFile ?: ''

        this.trigger = config.trigger ?: '~'

        // Full setup and configuration waits for a signal from onShardReady.
    }

    void init() {
        // Set client values to match bot-wide data.
        if(this.avatar.data != Image.forUser(this.client.ourUser).data) {
            LOGGER.info('Requested avatar (${this.avatar.data}) doesn\'t match current avatar (${Image.forUser(this.client.ourUser).data}).')
            this.client.changeAvatar(this.avatar)
        }
        if(this.username != this.client.ourUser.name) {
            this.client.changeUsername(this.username)
        }
        switch(this.status) {
            case 'online': this.client.online(this.playing)
                break
            case 'idle': this.client.idle(this.playing)
                break
            case 'streaming': this.client.streaming(this.playing, this.streamUrl)
                break
            default:
                LOGGER.warn("Status '${this.status}' is invalid. Status was not changed.")
        }

        // Load servers.
        servers = [:]

        List guilds = this.client.guilds
        for(guild in guilds) {
            long id = guild.id
            Map config = FileManager.readAsMap("$id/config.json")
            if(!config) {
                String newConfig = FileManager.read('sample/sample.config.json')
                newConfig = TemplateManager.fillTemplateWithData([
                        name: guild.name,
                        id: guild.id,
                        nick: (this.client.ourUser.getNicknameForGuild(guild) ?: '')
                    ],
                    newConfig)
                newConfig = newConfig.replace('"[', '[').replace(']"', ']') // Remove array values from quotes.
                FileManager.write("$id/config.json", newConfig)
                config = FileManager.readAsMap("$id/config.json")
            }
            // Then actually make the objects.
        }
    }

    /**
     * * * * * * * * * * *
     *   Setup Methods   *
     * * * * * * * * * * *
     */

    /**
     * Gets the configuration file, looking first for '/src/main/resources/config.json' and then
     * for '/src/main/resources/sample.config.json'. If neither is found, the program aborts.
     * @return A map representation of the contents of the configuration file.
     */
    Map getConfig() {
        Map config = FileManager.readAsMap('config.json')
        if(!config) {
            LOGGER.error("No config file found. Using the contents of '/src/main/resources/sample.config.json'.")
            FileManager.write('config.json', FileManager.read('sample.config.json'))
            config = FileManager.readAsMap('config.json')
            if(!config) {
                LOGGER.error("Could not read '/src/main/resources/sample.config.json'. Aborting.")
                System.exit(1)
            }
        }
        return config
    }

    /**
     * Creates the client used to communicate with Discord and logs in with the token given.
     * @param token The app bot user token, which can be retrieved by going to https://discordapp.com/developers/applications/me/,
     *     selecting your app, and, under the "App Bot User" section, clicking to reveal the token.
     */
    IDiscordClient makeClient(String token) {
        IDiscordClient client
        client = new ClientBuilder().withToken(token).build()
        client.getDispatcher().registerListener(this)
        return client
    }

    /**
     * Sets the logging level of the logger. If passed something other than
     * 'all', 'trace', 'debug', 'info', 'warn', 'error', or 'none', defaults to 'warn'.
     * @param level The logging level to set the logger to.
     */
    void setLogLevel(String level) {
        this.logLevel = level
        switch(level) {
            case 'all':
            case 'trace': LOGGER.setLevel(Level.TRACE)
                Discord4J.LOGGER.setLevel(Level.TRACE)
                break
            case 'debug': LOGGER.setLevel(Level.DEBUG)
                Discord4J.LOGGER.setLevel(Level.DEBUG)
                break
            case 'info': LOGGER.setLevel(Level.INFO)
                Discord4J.LOGGER.setLevel(Level.INFO)
                break
            case 'warn': LOGGER.setLevel(Level.WARN)
                Discord4J.LOGGER.setLevel(Level.WARN)
                break
            case 'error': LOGGER.setLevel(Level.ERROR)
                Discord4J.LOGGER.setLevel(Level.ERROR)
                break
            case 'none': LOGGER.setLevel(Level.OFF)
                Discord4J.LOGGER.setLevel(Level.OFF)
                break
            default: LOGGER.setLevel(Level.WARN)
                Discord4J.LOGGER.setLevel(Level.WARN)
                LOGGER.warn("Logging level '$level' is invalid. Logging level set to 'warn'.")
                this.logLevel = 'warn'
        }
    }

    /**
     * Loads an Image from a file.
     * @param filePath The path of the file to load from, relative to /src/main/resources.
     * @return An Image of the file.
     */
    Image loadAvatarFromLocalFile(String filePath) {
        if(!filePath) {
            return null
        }
        File file = this.class.getClassLoader().getResource(config.avatar).getFile()
        if(file.exists()) {
            return Image.forFile()
        }
        LOGGER.error('Avatar file not found! Please make sure the filename in config.json matches the name of the image file.')
        System.exit(1)
    }

    /**
     * * * * * * * * * * *
     *  Core  Functions  *
     * * * * * * * * * * *
     */

    // Enable Custodian on a server.
    void enable(MessageReceivedEvent event) {
        // TODO Stub.
    }

    // Disable Custodian on a server.
    void disable(MessageReceivedEvent event) {
        // TODO Stub.
    }

    /**
     * * * * * * * * * * *
     * Event Subscribers *
     * * * * * * * * * * *
     */

    // TODO - Some event subscribers need to be updated to account for private messages as well as "core" functionality and other stuff like that.

    /**
     * From package events.guild
     *
     * Used: GuildTransferOwnershipEvent, GuildUpdateEvent
     * Unused: AllUsersRecievedEvent, GuildCreateEvent, GuildEmojisUpdateEvent, GuildEvent, GuildLeaveEvent, GuildUnavailableEvent
     */
    @EventSubscriber
    void onGuildOwnershiptransferred(GuildTransferOwnershipEvent event) {
        servers[event.guild.id]?.onGuildOwnershiptransferred(event)
    }

    @EventSubscriber
    void onGuildUpdate(GuildUpdateEvent event) {
        servers[event.guild.id]?.onGuildUpdate(event)
    }

    /**
     * From package events.guild.channel
     *
     * Used: ChannelCreateEvent, ChannelDeleteEvent, ChannelUpdateEvent
     * Unused: ChannelEvent, TypingEvent
     */
    @EventSubscriber
    void onChannelCreated(ChannelCreateEvent event) {
        servers[event.guild.id]?.onChannelCreated(event)
    }

    @EventSubscriber
    void onChannelDeleted(ChannelDeleteEvent event) {
        servers[event.guild.id]?.onChannelDeleted(event)
    }

    @EventSubscriber
    void onChannelUpdated(ChannelUpdateEvent event) {
        servers[event.guild.id]?.onChannelUpdated(event)
    }

    /**
     * From package events.guild.channel.message
     *
     * Used: MessageDeleteEvent, MessagePinEvent, MessageReceivedEvent, MessageUnpinEvent, MessageUpdateEvent
     * Unused: MentionEvent, MessageEmbedEvent, MessageEvent, MessageSendEvent
     */
    @EventSubscriber
    void onMessageDelete(MessageDeleteEvent event) {
        servers[event.guild.id]?.onMessageDelete(event)
    }

    @EventSubscriber
    void onMessagePin(MessagePinEvent event) {
        servers[event.guild.id]?.onMessagePin(event)
    }

    @EventSubscriber
    void onMessageReceived(MessageReceivedEvent event) {
        servers[event.guild.id]?.onMessageReceived(event)
    }

    @EventSubscriber
    void onMessageUnpin(MessageUnpinEvent event) {
        servers[event.guild.id]?.onMessageUnpin(event)
    }

    @EventSubscriber
    void onMessageUpdate(MessageUpdateEvent event) {
        servers[event.guild.id]?.onMessageUpdate(event)
    }

    /**
     * From package events.guild.channel.message.reaction
     *
     * Used: ReactionAddEvent, ReactionRemoveEvent
     * Unused: ReactionEvent
     */
    @EventSubscriber
    void onReactionAdd(ReactionAddEvent event) {
        servers[event.guild.id]?.onReactionAdd(event)
    }

    @EventSubscriber
    void onReactionRemove(ReactionRemoveEvent event) {
        servers[event.guild.id]?.onReactionRemove(event)
    }

    /**
     * From package events.guild.channel.webhook
     *
     * Used:
     * Unused:WebhookCreateEvent, WebhookDeleteEvenr, WebhookEvent, WebhookUpdateEvent
     */

    /**
     * From package events.guild.member
     *
     * Used: NicknameChangedEvent, UserBanEvent, UserJoinEvent, UserLeaveEvent, UserPardonEvent, UserRoleUpdateEvent
     * Unused: GuildMemberEvent
     */
    @EventSubscriber
    void onNicknameChanged(NicknameChangedEvent event) {
        servers[event.guild.id]?.onNicknameChanged(event)
    }

    @EventSubscriber
    void onUserBan(UserBanEvent event) {
        servers[event.guild.id]?.onUserBan(event)
    }

    @EventSubscriber
    void onUserJoin(UserJoinEvent event) {
        servers[event.guild.id]?.onUserJoin(event)
    }

    @EventSubscriber
    void onUserLeave(UserLeaveEvent event) {
        servers[event.guild.id]?.onUserLeave(event)
    }

    @EventSubscriber
    void onUserPardon(UserPardonEvent event) {
        servers[event.guild.id]?.onUserPardon(event)
    }

    @EventSubscriber
    void onUserRoleUpdate(UserRoleUpdateEvent event) {
        servers[event.guild.id]?.onUserRoleUpdate(event)
    }

    /**
     * From package events.guild.role
     *
     * Used: RoleCreateEvent, RoleDeleteEvent, RoleUpdateEvent
     * Unused: RoleEvent
     */
    @EventSubscriber
    void onRoleCreate(RoleCreateEvent event) {
        servers[event.guild.id]?.onRoleCreate(event)
    }

    @EventSubscriber
    void onRoleDelete(RoleDeleteEvent event) {
        servers[event.guild.id]?.onRoleDelete(event)
    }

    @EventSubscriber
    void onRoleUpdate(RoleUpdateEvent event) {
        servers[event.guild.id]?.onRoleUpdate(event)
    }

    /**
     * From package events.guild.voice
     *
     * Used:
     * Unused: VoiceChannelCreateEvent, VoiceChannelDeleteEvent, VoiceChannelEvent, VoiceChannelUpdateEvent, VoiceDisconnectedEvent, VoicePingEvent
     */

    /**
     * From package events.guild.voice.user
     *
     * Used:
     * Unused: UserSpeakingEvent, UserVoiceChannelEvent, UserVoiceChannelJoinEvent, UserVoiceChannelLeaveEvent, UserVoiceChannelMoveEvent
     */

    /**
     * From package events.module
     *
     * Used:
     * Unused: ModuleDisabledEvent, ModuleEnabledEvent, ModuleEvent
     */

    /**
     * From package events.shard
     *
     * Used: DisconnectedEvent, ShardReadyEvent
     * Unused: LoginEvent, ReconnectFailureEvent, ReconnectSuccessEvent, ResumedEvent, ShardEvent
     */
    @EventSubscriber
    void onDisconnected(DisconnectedEvent event) {
        for(entry in servers) {
            entry.value.onDisconnected(event)
        }
    }

    @EventSubscriber
    void onShardReady(ShardReadyEvent event) {
        init()
        for(entry in servers) {
            entry.value.onShardReady(event)
        }
    }

    /**
     * From package events.user
     *
     * Used: PresenceUpdateEvent, UserUpdateEvent
     * Unused: UserEvent
     */
    @EventSubscriber
    void onPresenceUpdate(PresenceUpdateEvent event) {
        for(entry in servers) {
            entry.value.onPresenceUpdate(event)
        }
    }

    @EventSubscriber
    void onUserUpdate(UserUpdateEvent event) {
        for(entry in servers) {
            entry.value.onUserUpdate(event)
        }
    }
}
