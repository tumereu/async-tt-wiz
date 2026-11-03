package arcanum.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A keyword ability (CR 702) as written on the card, with exactly its values: `Keyword.Flying`,
 * `Keyword.Crew(3)`, `Keyword.Equip(cost)`. It stays a keyword ability in the definition and in play, so "creatures
 * with flying" and "loses flying" work; what it does for the engine comes from the shared expansion table
 * ([KeywordExpansions]): flying → [Mechanic.CanBeBlockedOnlyBy], deathtouch → [Mechanic.Deathtouch], equip →
 * an activated ability, and so on.
 */
@Serializable
sealed interface Keyword : Ability {
    /** Which keyword this is, without its values; used wherever cards refer to a keyword by name. */
    val kind: KeywordKind
    /** Printed name, as at the start of an oracle line. */
    val text: String get() = kind.text
    /** The rule defining it in the pinned rules version ([ComprehensiveRules]). */
    val rule: String get() = kind.rule

    /** Deathtouch (CR 702.2) */
    @Serializable @SerialName("deathtouch") data object Deathtouch : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "deathtouch"
        override val text: String get() = "Deathtouch"; override val rule: String get() = "702.2"
    }
    /** Defender (CR 702.3) */
    @Serializable @SerialName("defender") data object Defender : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "defender"
        override val text: String get() = "Defender"; override val rule: String get() = "702.3"
    }
    /** Double strike (CR 702.4) */
    @Serializable @SerialName("double-strike") data object DoubleStrike : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "double-strike"
        override val text: String get() = "Double strike"; override val rule: String get() = "702.4"
    }
    /** First strike (CR 702.7) */
    @Serializable @SerialName("first-strike") data object FirstStrike : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "first-strike"
        override val text: String get() = "First strike"; override val rule: String get() = "702.7"
    }
    /** Flash (CR 702.8) */
    @Serializable @SerialName("flash") data object Flash : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "flash"
        override val text: String get() = "Flash"; override val rule: String get() = "702.8"
    }
    /** Flying (CR 702.9) */
    @Serializable @SerialName("flying") data object Flying : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "flying"
        override val text: String get() = "Flying"; override val rule: String get() = "702.9"
    }
    /** Haste (CR 702.10) */
    @Serializable @SerialName("haste") data object Haste : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "haste"
        override val text: String get() = "Haste"; override val rule: String get() = "702.10"
    }
    /** Hexproof (CR 702.11) */
    @Serializable @SerialName("hexproof") data object Hexproof : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "hexproof"
        override val text: String get() = "Hexproof"; override val rule: String get() = "702.11"
    }
    /** Indestructible (CR 702.12) */
    @Serializable @SerialName("indestructible") data object Indestructible : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "indestructible"
        override val text: String get() = "Indestructible"; override val rule: String get() = "702.12"
    }
    /** Intimidate (CR 702.13) */
    @Serializable @SerialName("intimidate") data object Intimidate : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "intimidate"
        override val text: String get() = "Intimidate"; override val rule: String get() = "702.13"
    }
    /** Lifelink (CR 702.15) */
    @Serializable @SerialName("lifelink") data object Lifelink : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "lifelink"
        override val text: String get() = "Lifelink"; override val rule: String get() = "702.15"
    }
    /** Reach (CR 702.17) */
    @Serializable @SerialName("reach") data object Reach : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "reach"
        override val text: String get() = "Reach"; override val rule: String get() = "702.17"
    }
    /** Shroud (CR 702.18) */
    @Serializable @SerialName("shroud") data object Shroud : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "shroud"
        override val text: String get() = "Shroud"; override val rule: String get() = "702.18"
    }
    /** Trample (CR 702.19) */
    @Serializable @SerialName("trample") data object Trample : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "trample"
        override val text: String get() = "Trample"; override val rule: String get() = "702.19"
    }
    /** Vigilance (CR 702.20) */
    @Serializable @SerialName("vigilance") data object Vigilance : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "vigilance"
        override val text: String get() = "Vigilance"; override val rule: String get() = "702.20"
    }
    /** Banding (CR 702.22) */
    @Serializable @SerialName("banding") data object Banding : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "banding"
        override val text: String get() = "Banding"; override val rule: String get() = "702.22"
    }
    /** Flanking (CR 702.25) */
    @Serializable @SerialName("flanking") data object Flanking : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "flanking"
        override val text: String get() = "Flanking"; override val rule: String get() = "702.25"
    }
    /** Phasing (CR 702.26) */
    @Serializable @SerialName("phasing") data object Phasing : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "phasing"
        override val text: String get() = "Phasing"; override val rule: String get() = "702.26"
    }
    /** Shadow (CR 702.28) */
    @Serializable @SerialName("shadow") data object Shadow : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "shadow"
        override val text: String get() = "Shadow"; override val rule: String get() = "702.28"
    }
    /** Horsemanship (CR 702.31) */
    @Serializable @SerialName("horsemanship") data object Horsemanship : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "horsemanship"
        override val text: String get() = "Horsemanship"; override val rule: String get() = "702.31"
    }
    /** Fear (CR 702.36) */
    @Serializable @SerialName("fear") data object Fear : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "fear"
        override val text: String get() = "Fear"; override val rule: String get() = "702.36"
    }
    /** Provoke (CR 702.39) */
    @Serializable @SerialName("provoke") data object Provoke : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "provoke"
        override val text: String get() = "Provoke"; override val rule: String get() = "702.39"
    }
    /** Storm (CR 702.40) */
    @Serializable @SerialName("storm") data object Storm : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "storm"
        override val text: String get() = "Storm"; override val rule: String get() = "702.40"
    }
    /** Sunburst (CR 702.44) */
    @Serializable @SerialName("sunburst") data object Sunburst : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "sunburst"
        override val text: String get() = "Sunburst"; override val rule: String get() = "702.44"
    }
    /** Epic (CR 702.50) */
    @Serializable @SerialName("epic") data object Epic : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "epic"
        override val text: String get() = "Epic"; override val rule: String get() = "702.50"
    }
    /** Convoke (CR 702.51) */
    @Serializable @SerialName("convoke") data object Convoke : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "convoke"
        override val text: String get() = "Convoke"; override val rule: String get() = "702.51"
    }
    /** Haunt (CR 702.55) */
    @Serializable @SerialName("haunt") data object Haunt : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "haunt"
        override val text: String get() = "Haunt"; override val rule: String get() = "702.55"
    }
    /** Split second (CR 702.61) */
    @Serializable @SerialName("split-second") data object SplitSecond : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "split-second"
        override val text: String get() = "Split second"; override val rule: String get() = "702.61"
    }
    /** Delve (CR 702.66) */
    @Serializable @SerialName("delve") data object Delve : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "delve"
        override val text: String get() = "Delve"; override val rule: String get() = "702.66"
    }
    /** Gravestorm (CR 702.69) */
    @Serializable @SerialName("gravestorm") data object Gravestorm : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "gravestorm"
        override val text: String get() = "Gravestorm"; override val rule: String get() = "702.69"
    }
    /** Changeling (CR 702.73) */
    @Serializable @SerialName("changeling") data object Changeling : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "changeling"
        override val text: String get() = "Changeling"; override val rule: String get() = "702.73"
    }
    /** Conspire (CR 702.78) */
    @Serializable @SerialName("conspire") data object Conspire : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "conspire"
        override val text: String get() = "Conspire"; override val rule: String get() = "702.78"
    }
    /** Persist (CR 702.79) */
    @Serializable @SerialName("persist") data object Persist : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "persist"
        override val text: String get() = "Persist"; override val rule: String get() = "702.79"
    }
    /** Wither (CR 702.80) */
    @Serializable @SerialName("wither") data object Wither : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "wither"
        override val text: String get() = "Wither"; override val rule: String get() = "702.80"
    }
    /** Retrace (CR 702.81) */
    @Serializable @SerialName("retrace") data object Retrace : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "retrace"
        override val text: String get() = "Retrace"; override val rule: String get() = "702.81"
    }
    /** Exalted (CR 702.83) */
    @Serializable @SerialName("exalted") data object Exalted : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "exalted"
        override val text: String get() = "Exalted"; override val rule: String get() = "702.83"
    }
    /** Cascade (CR 702.85) */
    @Serializable @SerialName("cascade") data object Cascade : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "cascade"
        override val text: String get() = "Cascade"; override val rule: String get() = "702.85"
    }
    /** Rebound (CR 702.88) */
    @Serializable @SerialName("rebound") data object Rebound : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "rebound"
        override val text: String get() = "Rebound"; override val rule: String get() = "702.88"
    }
    /** Umbra armor (CR 702.89) */
    @Serializable @SerialName("umbra-armor") data object UmbraArmor : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "umbra-armor"
        override val text: String get() = "Umbra armor"; override val rule: String get() = "702.89"
    }
    /** Infect (CR 702.90) */
    @Serializable @SerialName("infect") data object Infect : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "infect"
        override val text: String get() = "Infect"; override val rule: String get() = "702.90"
    }
    /** Battle cry (CR 702.91) */
    @Serializable @SerialName("battle-cry") data object BattleCry : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "battle-cry"
        override val text: String get() = "Battle cry"; override val rule: String get() = "702.91"
    }
    /** Living weapon (CR 702.92) */
    @Serializable @SerialName("living-weapon") data object LivingWeapon : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "living-weapon"
        override val text: String get() = "Living weapon"; override val rule: String get() = "702.92"
    }
    /** Undying (CR 702.93) */
    @Serializable @SerialName("undying") data object Undying : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "undying"
        override val text: String get() = "Undying"; override val rule: String get() = "702.93"
    }
    /** Soulbond (CR 702.95) */
    @Serializable @SerialName("soulbond") data object Soulbond : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "soulbond"
        override val text: String get() = "Soulbond"; override val rule: String get() = "702.95"
    }
    /** Unleash (CR 702.98) */
    @Serializable @SerialName("unleash") data object Unleash : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "unleash"
        override val text: String get() = "Unleash"; override val rule: String get() = "702.98"
    }
    /** Cipher (CR 702.99) */
    @Serializable @SerialName("cipher") data object Cipher : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "cipher"
        override val text: String get() = "Cipher"; override val rule: String get() = "702.99"
    }
    /** Evolve (CR 702.100) */
    @Serializable @SerialName("evolve") data object Evolve : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "evolve"
        override val text: String get() = "Evolve"; override val rule: String get() = "702.100"
    }
    /** Extort (CR 702.101) */
    @Serializable @SerialName("extort") data object Extort : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "extort"
        override val text: String get() = "Extort"; override val rule: String get() = "702.101"
    }
    /** Fuse (CR 702.102) */
    @Serializable @SerialName("fuse") data object Fuse : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "fuse"
        override val text: String get() = "Fuse"; override val rule: String get() = "702.102"
    }
    /** Dethrone (CR 702.105) */
    @Serializable @SerialName("dethrone") data object Dethrone : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "dethrone"
        override val text: String get() = "Dethrone"; override val rule: String get() = "702.105"
    }
    /** Hidden agenda (CR 702.106) */
    @Serializable @SerialName("hidden-agenda") data object HiddenAgenda : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "hidden-agenda"
        override val text: String get() = "Hidden agenda"; override val rule: String get() = "702.106"
    }
    /** Prowess (CR 702.108) */
    @Serializable @SerialName("prowess") data object Prowess : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "prowess"
        override val text: String get() = "Prowess"; override val rule: String get() = "702.108"
    }
    /** Exploit (CR 702.110) */
    @Serializable @SerialName("exploit") data object Exploit : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "exploit"
        override val text: String get() = "Exploit"; override val rule: String get() = "702.110"
    }
    /** Menace (CR 702.111) */
    @Serializable @SerialName("menace") data object Menace : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "menace"
        override val text: String get() = "Menace"; override val rule: String get() = "702.111"
    }
    /** Devoid (CR 702.114) */
    @Serializable @SerialName("devoid") data object Devoid : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "devoid"
        override val text: String get() = "Devoid"; override val rule: String get() = "702.114"
    }
    /** Ingest (CR 702.115) */
    @Serializable @SerialName("ingest") data object Ingest : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "ingest"
        override val text: String get() = "Ingest"; override val rule: String get() = "702.115"
    }
    /** Myriad (CR 702.116) */
    @Serializable @SerialName("myriad") data object Myriad : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "myriad"
        override val text: String get() = "Myriad"; override val rule: String get() = "702.116"
    }
    /** Skulk (CR 702.118) */
    @Serializable @SerialName("skulk") data object Skulk : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "skulk"
        override val text: String get() = "Skulk"; override val rule: String get() = "702.118"
    }
    /** Melee (CR 702.121) */
    @Serializable @SerialName("melee") data object Melee : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "melee"
        override val text: String get() = "Melee"; override val rule: String get() = "702.121"
    }
    /** Partner (CR 702.124) */
    @Serializable @SerialName("partner") data object Partner : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "partner"
        override val text: String get() = "Partner"; override val rule: String get() = "702.124"
    }
    /** Undaunted (CR 702.125) */
    @Serializable @SerialName("undaunted") data object Undaunted : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "undaunted"
        override val text: String get() = "Undaunted"; override val rule: String get() = "702.125"
    }
    /** Improvise (CR 702.126) */
    @Serializable @SerialName("improvise") data object Improvise : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "improvise"
        override val text: String get() = "Improvise"; override val rule: String get() = "702.126"
    }
    /** Aftermath (CR 702.127) */
    @Serializable @SerialName("aftermath") data object Aftermath : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "aftermath"
        override val text: String get() = "Aftermath"; override val rule: String get() = "702.127"
    }
    /** Ascend (CR 702.131) */
    @Serializable @SerialName("ascend") data object Ascend : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "ascend"
        override val text: String get() = "Ascend"; override val rule: String get() = "702.131"
    }
    /** Assist (CR 702.132) */
    @Serializable @SerialName("assist") data object Assist : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "assist"
        override val text: String get() = "Assist"; override val rule: String get() = "702.132"
    }
    /** Jump-start (CR 702.133) */
    @Serializable @SerialName("jump-start") data object JumpStart : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "jump-start"
        override val text: String get() = "Jump-start"; override val rule: String get() = "702.133"
    }
    /** Mentor (CR 702.134) */
    @Serializable @SerialName("mentor") data object Mentor : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "mentor"
        override val text: String get() = "Mentor"; override val rule: String get() = "702.134"
    }
    /** Riot (CR 702.136) */
    @Serializable @SerialName("riot") data object Riot : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "riot"
        override val text: String get() = "Riot"; override val rule: String get() = "702.136"
    }
    /** Demonstrate (CR 702.144) */
    @Serializable @SerialName("demonstrate") data object Demonstrate : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "demonstrate"
        override val text: String get() = "Demonstrate"; override val rule: String get() = "702.144"
    }
    /** Daybound (CR 702.145b) */
    @Serializable @SerialName("daybound") data object Daybound : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "daybound"
        override val text: String get() = "Daybound"; override val rule: String get() = "702.145b"
    }
    /** Nightbound (CR 702.145e) */
    @Serializable @SerialName("nightbound") data object Nightbound : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "nightbound"
        override val text: String get() = "Nightbound"; override val rule: String get() = "702.145e"
    }
    /** Decayed (CR 702.147) */
    @Serializable @SerialName("decayed") data object Decayed : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "decayed"
        override val text: String get() = "Decayed"; override val rule: String get() = "702.147"
    }
    /** Training (CR 702.149) */
    @Serializable @SerialName("training") data object Training : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "training"
        override val text: String get() = "Training"; override val rule: String get() = "702.149"
    }
    /** Compleated (CR 702.150) */
    @Serializable @SerialName("compleated") data object Compleated : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "compleated"
        override val text: String get() = "Compleated"; override val rule: String get() = "702.150"
    }
    /** Enlist (CR 702.154) */
    @Serializable @SerialName("enlist") data object Enlist : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "enlist"
        override val text: String get() = "Enlist"; override val rule: String get() = "702.154"
    }
    /** Read ahead (CR 702.155) */
    @Serializable @SerialName("read-ahead") data object ReadAhead : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "read-ahead"
        override val text: String get() = "Read ahead"; override val rule: String get() = "702.155"
    }
    /** Ravenous (CR 702.156) */
    @Serializable @SerialName("ravenous") data object Ravenous : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "ravenous"
        override val text: String get() = "Ravenous"; override val rule: String get() = "702.156"
    }
    /** Living metal (CR 702.161) */
    @Serializable @SerialName("living-metal") data object LivingMetal : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "living-metal"
        override val text: String get() = "Living metal"; override val rule: String get() = "702.161"
    }
    /** For Mirrodin! (CR 702.163) */
    @Serializable @SerialName("for-mirrodin") data object ForMirrodin : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "for-mirrodin"
        override val text: String get() = "For Mirrodin!"; override val rule: String get() = "702.163"
    }
    /** Bargain (CR 702.166) */
    @Serializable @SerialName("bargain") data object Bargain : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "bargain"
        override val text: String get() = "Bargain"; override val rule: String get() = "702.166"
    }
    /** Spree (CR 702.172) */
    @Serializable @SerialName("spree") data object Spree : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "spree"
        override val text: String get() = "Spree"; override val rule: String get() = "702.172"
    }
    /** Start your engines! (CR 702.179) */
    @Serializable @SerialName("start-your-engines") data object StartYourEngines : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "start-your-engines"
        override val text: String get() = "Start your engines!"; override val rule: String get() = "702.179"
    }
    /** Job select (CR 702.182) */
    @Serializable @SerialName("job-select") data object JobSelect : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "job-select"
        override val text: String get() = "Job select"; override val rule: String get() = "702.182"
    }
    /** Tiered (CR 702.183) */
    @Serializable @SerialName("tiered") data object Tiered : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "tiered"
        override val text: String get() = "Tiered"; override val rule: String get() = "702.183"
    }
    /** Station (CR 702.184) */
    @Serializable @SerialName("station") data object Station : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "station"
        override val text: String get() = "Station"; override val rule: String get() = "702.184"
    }
    /** Increment (CR 702.191) */
    @Serializable @SerialName("increment") data object Increment : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "increment"
        override val text: String get() = "Increment"; override val rule: String get() = "702.191"
    }
    // sos
    /** Paradigm (CR 702.192): "If this is the first time a spell you control with this spell's name has resolved this game, at the
     * beginning of each of your precombat main phases for the rest of the game, create a copy of this object in exile. You may cast the copy
     * without paying its mana cost" and "Exile this spell." [KeywordExpansions] writes it out. */
    @Serializable @SerialName("paradigm") data object Paradigm : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "paradigm"
        override val text: String get() = "Paradigm"; override val rule: String get() = "702.192"
    }
    /** Storied (CR 702.195) */
    @Serializable @SerialName("storied") data object Storied : Keyword, KeywordKind {
        override val kind: KeywordKind get() = this; override val id: String get() = "storied"
        override val text: String get() = "Storied"; override val rule: String get() = "702.195"
    }
    /** Rampage (CR 702.23) */
    @Serializable @SerialName("rampage") data class Rampage(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "rampage"; override val text: String get() = "Rampage"; override val rule: String get() = "702.23" }
    }
    /** Fading (CR 702.32) */
    @Serializable @SerialName("fading") data class Fading(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "fading"; override val text: String get() = "Fading"; override val rule: String get() = "702.32" }
    }
    /** Amplify (CR 702.38) */
    @Serializable @SerialName("amplify") data class Amplify(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "amplify"; override val text: String get() = "Amplify"; override val rule: String get() = "702.38" }
    }
    /** Modular (CR 702.43) */
    @Serializable @SerialName("modular") data class Modular(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "modular"; override val text: String get() = "Modular"; override val rule: String get() = "702.43" }
    }
    /** Bushido (CR 702.45) */
    @Serializable @SerialName("bushido") data class Bushido(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "bushido"; override val text: String get() = "Bushido"; override val rule: String get() = "702.45" }
    }
    /** Soulshift (CR 702.46) */
    @Serializable @SerialName("soulshift") data class Soulshift(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "soulshift"; override val text: String get() = "Soulshift"; override val rule: String get() = "702.46" }
    }
    /** Dredge (CR 702.52) */
    @Serializable @SerialName("dredge") data class Dredge(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "dredge"; override val text: String get() = "Dredge"; override val rule: String get() = "702.52" }
    }
    /** Bloodthirst (CR 702.54) */
    @Serializable @SerialName("bloodthirst") data class Bloodthirst(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "bloodthirst"; override val text: String get() = "Bloodthirst"; override val rule: String get() = "702.54" }
    }
    /** Graft (CR 702.58) */
    @Serializable @SerialName("graft") data class Graft(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "graft"; override val text: String get() = "Graft"; override val rule: String get() = "702.58" }
    }
    /** Ripple (CR 702.60) */
    @Serializable @SerialName("ripple") data class Ripple(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "ripple"; override val text: String get() = "Ripple"; override val rule: String get() = "702.60" }
    }
    /** Vanishing (CR 702.63) */
    @Serializable @SerialName("vanishing") data class Vanishing(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "vanishing"; override val text: String get() = "Vanishing"; override val rule: String get() = "702.63" }
    }
    /** Absorb (CR 702.64) */
    @Serializable @SerialName("absorb") data class Absorb(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "absorb"; override val text: String get() = "Absorb"; override val rule: String get() = "702.64" }
    }
    /** Frenzy (CR 702.68) */
    @Serializable @SerialName("frenzy") data class Frenzy(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "frenzy"; override val text: String get() = "Frenzy"; override val rule: String get() = "702.68" }
    }
    /** Poisonous (CR 702.70) */
    @Serializable @SerialName("poisonous") data class Poisonous(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "poisonous"; override val text: String get() = "Poisonous"; override val rule: String get() = "702.70" }
    }
    /** Hideaway (CR 702.75) */
    @Serializable @SerialName("hideaway") data class Hideaway(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "hideaway"; override val text: String get() = "Hideaway"; override val rule: String get() = "702.75" }
    }
    /** Devour (CR 702.82) */
    @Serializable @SerialName("devour") data class Devour(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "devour"; override val text: String get() = "Devour"; override val rule: String get() = "702.82" }
    }
    /** Annihilator (CR 702.86) */
    @Serializable @SerialName("annihilator") data class Annihilator(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "annihilator"; override val text: String get() = "Annihilator"; override val rule: String get() = "702.86" }
    }
    /** Tribute (CR 702.104) */
    @Serializable @SerialName("tribute") data class Tribute(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "tribute"; override val text: String get() = "Tribute"; override val rule: String get() = "702.104" }
    }
    /** Renown (CR 702.112) */
    @Serializable @SerialName("renown") data class Renown(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "renown"; override val text: String get() = "Renown"; override val rule: String get() = "702.112" }
    }
    /** Crew (CR 702.122) */
    @Serializable @SerialName("crew") data class Crew(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "crew"; override val text: String get() = "Crew"; override val rule: String get() = "702.122" }
    }
    /** Fabricate (CR 702.123) */
    @Serializable @SerialName("fabricate") data class Fabricate(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "fabricate"; override val text: String get() = "Fabricate"; override val rule: String get() = "702.123" }
    }
    /** Afflict (CR 702.130) */
    @Serializable @SerialName("afflict") data class Afflict(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "afflict"; override val text: String get() = "Afflict"; override val rule: String get() = "702.130" }
    }
    /** Afterlife (CR 702.135) */
    @Serializable @SerialName("afterlife") data class Afterlife(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "afterlife"; override val text: String get() = "Afterlife"; override val rule: String get() = "702.135" }
    }
    /** Casualty (CR 702.153) */
    @Serializable @SerialName("casualty") data class Casualty(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "casualty"; override val text: String get() = "Casualty"; override val rule: String get() = "702.153" }
    }
    /** Toxic (CR 702.164) */
    @Serializable @SerialName("toxic") data class Toxic(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "toxic"; override val text: String get() = "Toxic"; override val rule: String get() = "702.164" }
    }
    /** Backup (CR 702.165) */
    @Serializable @SerialName("backup") data class Backup(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "backup"; override val text: String get() = "Backup"; override val rule: String get() = "702.165" }
    }
    /** Saddle (CR 702.171) */
    @Serializable @SerialName("saddle") data class Saddle(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "saddle"; override val text: String get() = "Saddle"; override val rule: String get() = "702.171" }
    }
    /** Mobilize (CR 702.181) */
    @Serializable @SerialName("mobilize") data class Mobilize(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "mobilize"; override val text: String get() = "Mobilize"; override val rule: String get() = "702.181" }
    }
    /** Firebending (CR 702.189) */
    @Serializable @SerialName("firebending") data class Firebending(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "firebending"; override val text: String get() = "Firebending"; override val rule: String get() = "702.189" }
    }
    /** Teamwork (CR 702.194) */
    @Serializable @SerialName("teamwork") data class Teamwork(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "teamwork"; override val text: String get() = "Teamwork"; override val rule: String get() = "702.194" }
    }
    /** Ward (CR 702.21) */
    @Serializable @SerialName("ward") data class Ward(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "ward"; override val text: String get() = "Ward"; override val rule: String get() = "702.21" }
    }
    /** Cumulative upkeep (CR 702.24) */
    @Serializable @SerialName("cumulative-upkeep") data class CumulativeUpkeep(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "cumulative-upkeep"; override val text: String get() = "Cumulative upkeep"; override val rule: String get() = "702.24" }
    }
    /** Buyback (CR 702.27) */
    @Serializable @SerialName("buyback") data class Buyback(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "buyback"; override val text: String get() = "Buyback"; override val rule: String get() = "702.27" }
    }
    /** Cycling (CR 702.29) */
    @Serializable @SerialName("cycling") data class Cycling(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "cycling"; override val text: String get() = "Cycling"; override val rule: String get() = "702.29" }
    }
    /** Echo (CR 702.30) */
    @Serializable @SerialName("echo") data class Echo(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "echo"; override val text: String get() = "Echo"; override val rule: String get() = "702.30" }
    }
    /** Multikicker is a kicker (CR 702.33c): "kicked" and "with kicker" find it, so its kind is kicker's. */
    @Serializable @SerialName("multikicker") data class Multikicker(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kicker.Kind
        override val text: String get() = "Multikicker"; override val rule: String get() = "702.33c"
    }
    /** Flashback (CR 702.34) */
    @Serializable @SerialName("flashback") data class Flashback(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "flashback"; override val text: String get() = "Flashback"; override val rule: String get() = "702.34" }
    }
    /** Madness (CR 702.35) */
    @Serializable @SerialName("madness") data class Madness(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "madness"; override val text: String get() = "Madness"; override val rule: String get() = "702.35" }
    }
    /** Morph (CR 702.37) */
    @Serializable @SerialName("morph") data class Morph(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "morph"; override val text: String get() = "Morph"; override val rule: String get() = "702.37" }
    }
    /** Megamorph is a morph (CR 702.37b): its kind is morph's. */
    @Serializable @SerialName("megamorph") data class Megamorph(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Morph.Kind
        override val text: String get() = "Megamorph"; override val rule: String get() = "702.37b"
    }
    /** Entwine (CR 702.42) */
    @Serializable @SerialName("entwine") data class Entwine(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "entwine"; override val text: String get() = "Entwine"; override val rule: String get() = "702.42" }
    }
    /** Ninjutsu (CR 702.49) */
    @Serializable @SerialName("ninjutsu") data class Ninjutsu(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "ninjutsu"; override val text: String get() = "Ninjutsu"; override val rule: String get() = "702.49" }
    }
    /** Transmute (CR 702.53) */
    @Serializable @SerialName("transmute") data class Transmute(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "transmute"; override val text: String get() = "Transmute"; override val rule: String get() = "702.53" }
    }
    /** Fortify (CR 702.67) */
    @Serializable @SerialName("fortify") data class Fortify(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "fortify"; override val text: String get() = "Fortify"; override val rule: String get() = "702.67" }
    }
    /** Replicate (CR 702.56) */
    @Serializable @SerialName("replicate") data class Replicate(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "replicate"; override val text: String get() = "Replicate"; override val rule: String get() = "702.56" }
    }
    /** Recover (CR 702.59) */
    @Serializable @SerialName("recover") data class Recover(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "recover"; override val text: String get() = "Recover"; override val rule: String get() = "702.59" }
    }
    /** Evoke (CR 702.74) */
    @Serializable @SerialName("evoke") data class Evoke(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "evoke"; override val text: String get() = "Evoke"; override val rule: String get() = "702.74" }
    }
    /** Prowl (CR 702.76) */
    @Serializable @SerialName("prowl") data class Prowl(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "prowl"; override val text: String get() = "Prowl"; override val rule: String get() = "702.76" }
    }
    /** Unearth (CR 702.84) */
    @Serializable @SerialName("unearth") data class Unearth(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "unearth"; override val text: String get() = "Unearth"; override val rule: String get() = "702.84" }
    }
    /** Level up (CR 702.87) */
    @Serializable @SerialName("level-up") data class LevelUp(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "level-up"; override val text: String get() = "Level up"; override val rule: String get() = "702.87" }
    }
    /** Miracle (CR 702.94) */
    @Serializable @SerialName("miracle") data class Miracle(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "miracle"; override val text: String get() = "Miracle"; override val rule: String get() = "702.94" }
    }
    /** Overload (CR 702.96) */
    /** [instructions]: the spell's instructions with "target" replaced by "each" (CR 702.96b), used when the overload
     * cost is paid; written by the parser. */
    @Serializable @SerialName("overload") data class Overload(override val cost: Cost, val instructions: Instructions? = null) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "overload"; override val text: String get() = "Overload"; override val rule: String get() = "702.96" }
    }
    /** Scavenge (CR 702.97) */
    @Serializable @SerialName("scavenge") data class Scavenge(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "scavenge"; override val text: String get() = "Scavenge"; override val rule: String get() = "702.97" }
    }
    /** Bestow (CR 702.103) */
    @Serializable @SerialName("bestow") data class Bestow(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "bestow"; override val text: String get() = "Bestow"; override val rule: String get() = "702.103" }
    }
    /** Outlast (CR 702.107) */
    @Serializable @SerialName("outlast") data class Outlast(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "outlast"; override val text: String get() = "Outlast"; override val rule: String get() = "702.107" }
    }
    /** Dash (CR 702.109) */
    @Serializable @SerialName("dash") data class Dash(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "dash"; override val text: String get() = "Dash"; override val rule: String get() = "702.109" }
    }
    /** Surge (CR 702.117) */
    @Serializable @SerialName("surge") data class Surge(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "surge"; override val text: String get() = "Surge"; override val rule: String get() = "702.117" }
    }
    /** Emerge (CR 702.119) */
    @Serializable @SerialName("emerge") data class Emerge(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "emerge"; override val text: String get() = "Emerge"; override val rule: String get() = "702.119" }
    }
    /** Escalate (CR 702.120) */
    @Serializable @SerialName("escalate") data class Escalate(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "escalate"; override val text: String get() = "Escalate"; override val rule: String get() = "702.120" }
    }
    /** Embalm (CR 702.128) */
    @Serializable @SerialName("embalm") data class Embalm(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "embalm"; override val text: String get() = "Embalm"; override val rule: String get() = "702.128" }
    }
    /** Eternalize (CR 702.129) */
    @Serializable @SerialName("eternalize") data class Eternalize(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "eternalize"; override val text: String get() = "Eternalize"; override val rule: String get() = "702.129" }
    }
    /** Spectacle (CR 702.137) */
    @Serializable @SerialName("spectacle") data class Spectacle(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "spectacle"; override val text: String get() = "Spectacle"; override val rule: String get() = "702.137" }
    }
    /** Escape (CR 702.138) */
    @Serializable @SerialName("escape") data class Escape(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "escape"; override val text: String get() = "Escape"; override val rule: String get() = "702.138" }
    }
    /** Mutate (CR 702.140) */
    @Serializable @SerialName("mutate") data class Mutate(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "mutate"; override val text: String get() = "Mutate"; override val rule: String get() = "702.140" }
    }
    /** Encore (CR 702.141) */
    @Serializable @SerialName("encore") data class Encore(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "encore"; override val text: String get() = "Encore"; override val rule: String get() = "702.141" }
    }
    /** Foretell (CR 702.143) */
    @Serializable @SerialName("foretell") data class Foretell(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "foretell"; override val text: String get() = "Foretell"; override val rule: String get() = "702.143" }
    }
    /** Disturb (CR 702.146) */
    @Serializable @SerialName("disturb") data class Disturb(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "disturb"; override val text: String get() = "Disturb"; override val rule: String get() = "702.146" }
    }
    /** Cleave (CR 702.148) */
    @Serializable @SerialName("cleave") data class Cleave(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "cleave"; override val text: String get() = "Cleave"; override val rule: String get() = "702.148" }
    }
    /** Reconfigure (CR 702.151) */
    @Serializable @SerialName("reconfigure") data class Reconfigure(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "reconfigure"; override val text: String get() = "Reconfigure"; override val rule: String get() = "702.151" }
    }
    /** Blitz (CR 702.152) */
    @Serializable @SerialName("blitz") data class Blitz(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "blitz"; override val text: String get() = "Blitz"; override val rule: String get() = "702.152" }
    }
    /** Squad (CR 702.157) */
    @Serializable @SerialName("squad") data class Squad(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "squad"; override val text: String get() = "Squad"; override val rule: String get() = "702.157" }
    }
    /** Disguise (CR 702.168) */
    @Serializable @SerialName("disguise") data class Disguise(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "disguise"; override val text: String get() = "Disguise"; override val rule: String get() = "702.168" }
    }
    /** Plot (CR 702.170) */
    @Serializable @SerialName("plot") data class Plot(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "plot"; override val text: String get() = "Plot"; override val rule: String get() = "702.170" }
    }
    /** Freerunning (CR 702.173) */
    @Serializable @SerialName("freerunning") data class Freerunning(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "freerunning"; override val text: String get() = "Freerunning"; override val rule: String get() = "702.173" }
    }
    /** Offspring (CR 702.175) */
    @Serializable @SerialName("offspring") data class Offspring(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "offspring"; override val text: String get() = "Offspring"; override val rule: String get() = "702.175" }
    }
    /** Harmonize (CR 702.180) */
    @Serializable @SerialName("harmonize") data class Harmonize(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "harmonize"; override val text: String get() = "Harmonize"; override val rule: String get() = "702.180" }
    }
    /** Warp (CR 702.185) */
    @Serializable @SerialName("warp") data class Warp(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "warp"; override val text: String get() = "Warp"; override val rule: String get() = "702.185" }
    }
    /** Mayhem (CR 702.187) */
    @Serializable @SerialName("mayhem") data class Mayhem(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "mayhem"; override val text: String get() = "Mayhem"; override val rule: String get() = "702.187" }
    }
    /** Web-slinging (CR 702.188) */
    @Serializable @SerialName("web-slinging") data class WebSlinging(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "web-slinging"; override val text: String get() = "Web-slinging"; override val rule: String get() = "702.188" }
    }
    /** Sneak (CR 702.190) */
    @Serializable @SerialName("sneak") data class Sneak(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "sneak"; override val text: String get() = "Sneak"; override val rule: String get() = "702.190" }
    }
    /** Suspend (CR 702.62) */
    @Serializable @SerialName("suspend") data class Suspend(override val amount: Int, override val cost: Cost) : Keyword, HasAmount, HasCost {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "suspend"; override val text: String get() = "Suspend"; override val rule: String get() = "702.62" }
    }
    /** Reinforce (CR 702.77) */
    @Serializable @SerialName("reinforce") data class Reinforce(override val amount: Int, override val cost: Cost) : Keyword, HasAmount, HasCost {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "reinforce"; override val text: String get() = "Reinforce"; override val rule: String get() = "702.77" }
    }
    /** Awaken (CR 702.113) */
    @Serializable @SerialName("awaken") data class Awaken(override val amount: Int, override val cost: Cost) : Keyword, HasAmount, HasCost {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "awaken"; override val text: String get() = "Awaken"; override val rule: String get() = "702.113" }
    }
    /** Impending (CR 702.176) */
    @Serializable @SerialName("impending") data class Impending(override val amount: Int, override val cost: Cost) : Keyword, HasAmount, HasCost {
        init { requireAmount(amount) }
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "impending"; override val text: String get() = "Impending"; override val rule: String get() = "702.176" }
    }
    /** Equip {2}; "Equip legendary creature {3}" restricts what it attaches to ([onto]). (CR 702.6) */
    @Serializable @SerialName("equip") data class Equip(
        override val cost: Cost, val onto: ObjectFilter? = null,
        // fra
        /** "This ability costs {1} less to activate for each +1/+1 counter on the creature it targets" ([Objects.Target]
         * "creature" is the equip target, CR 601.2f, 602.2b). */
        val costChange: CostChange? = null,
    ) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "equip"; override val text: String get() = "Equip"; override val rule: String get() = "702.6" }
    }
    /** One or more kicker costs ("Kicker {1}{U} and/or {2}{B}"). (CR 702.33) */
    @Serializable @SerialName("kicker") data class Kicker(val costs: List<Cost>) : Keyword {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "kicker"; override val text: String get() = "Kicker"; override val rule: String get() = "702.33" }
    }
    /** Enchant creature / player / land you control …: what the Aura can target and enchant. (CR 702.5) */
    @Serializable @SerialName("enchant") data class Enchant(val what: TargetKind) : Keyword {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "enchant"; override val text: String get() = "Enchant"; override val rule: String get() = "702.5" }
    }
    /** Protection from [quality]; [from] matches the sources it protects from ("everything" = Any). (CR 702.16) */
    @Serializable @SerialName("protection") data class Protection(val from: ObjectFilter) : Keyword {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "protection"; override val text: String get() = "Protection"; override val rule: String get() = "702.16" }
    }
    /** Hexproof from a quality is a hexproof ability (CR 702.11d–e): "loses hexproof" and "with hexproof" find it, so its
     * kind is hexproof's. */
    @Serializable @SerialName("hexproof-from") data class HexproofFrom(val from: ObjectFilter) : Keyword {
        override val kind: KeywordKind get() = Hexproof
        override val text: String get() = "Hexproof from"; override val rule: String get() = "702.11d"
    }
    /** Swampwalk, nonbasic landwalk, …: [land] is the kind of land, [printed] the written word ("Swampwalk"). (CR 702.14) */
    @Serializable @SerialName("landwalk") data class Landwalk(val land: ObjectFilter, val printed: String) : Keyword {
        override val kind: KeywordKind get() = Kind
        override val text: String get() = printed
        companion object Kind : KeywordKind { override val id: String get() = "landwalk"; override val text: String get() = "Landwalk"; override val rule: String get() = "702.14" }
    }
    /** Affinity for artifacts / for Equipment … (CR 702.41) */
    @Serializable @SerialName("affinity") data class Affinity(val forWhat: ObjectFilter, val printed: String) : Keyword {
        override val kind: KeywordKind get() = Kind
        override val text: String get() = printed
        companion object Kind : KeywordKind { override val id: String get() = "affinity"; override val text: String get() = "Affinity"; override val rule: String get() = "702.41" }
    }
    /** Plainscycling, basic landcycling, …; a kind of cycling (CR 702.29f). Its [kind] is cycling, so "whenever you cycle" sees it. (CR 702.29e) */
    @Serializable @SerialName("typecycling") data class Typecycling(val cards: ObjectFilter, val printed: String, override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Cycling.Kind
        override val text: String get() = printed; override val rule: String get() = "702.29e"
    }
    /** Splice onto Arcane {1}{R}. (CR 702.47) */
    @Serializable @SerialName("splice") data class Splice(val onto: ObjectFilter, override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "splice"; override val text: String get() = "Splice"; override val rule: String get() = "702.47" }
    }
    /** Partner with [name]. (CR 702.124j) */
    @Serializable @SerialName("partner-with") data class PartnerWith(val partner: String) : Keyword {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "partner-with"; override val text: String get() = "Partner with"; override val rule: String get() = "702.124j" }
    }
    /** Commander ninjutsu [cost] (CR 702.49d): ninjutsu that also works from the command zone. */
    @Serializable @SerialName("commander-ninjutsu") data class CommanderNinjutsu(override val cost: Cost) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "commander-ninjutsu"; override val text: String get() = "Commander ninjutsu"; override val rule: String get() = "702.49d" }
    }
    /** Companion — a deck-construction condition (CR 702.139): [eachCard] when it is "each card in your starting deck
     * matches", otherwise [requirement] as printed. Outside the game, its owner may pay {3} to put it into their hand
     * as a special action once per game, during their main phase with the Stack empty (CR 702.139a, 116.2g). */
    @Serializable @SerialName("companion") data class CompanionAbility(val requirement: String, val eachCard: ObjectFilter? = null) : Keyword {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "companion"; override val text: String get() = "Companion"; override val rule: String get() = "702.139" }
    }
    /** Prototype {1}{R} — 2/1: alternative characteristics when cast prototyped. (CR 702.160) */
    @Serializable @SerialName("prototype") data class Prototype(override val cost: Cost, val power: Int, val toughness: Int) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "prototype"; override val text: String get() = "Prototype"; override val rule: String get() = "702.160" }
    }
    /** Gift a [gift] (CR 702.174): "As an additional cost to cast this spell, you may choose an opponent", and the gift
     * the chosen player gets: on an instant or sorcery before its other spell abilities, on a permanent when it enters
     * (702.174a, b, j). "The gift was promised" is [Condition.Paid] of this keyword (702.174k). */
    @Serializable @SerialName("gift") data class Gift(val gift: Gifted) : Keyword {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "gift"; override val text: String get() = "Gift"; override val rule: String get() = "702.174" }
    }
    /** A class level bar, "[cost]: Level [level]" with the [abilities] printed in its section (CR 716.2a): "[Cost]: This
     * Class's level becomes N. Activate only if this Class is level N-1 and only as a sorcery" and "As long as this Class
     * is level N or greater, it has [abilities]". */
    @Serializable @SerialName("class-level") data class ClassLevel(override val cost: Cost, val level: Int, val abilities: List<Ability>) : Keyword, HasCost {
        override val kind: KeywordKind get() = Kind
        companion object Kind : KeywordKind { override val id: String get() = "class-level"; override val text: String get() = "Level"; override val rule: String get() = "716.2" }
    }
}

/** What a gift gives the chosen opponent (CR 702.174d–i). */
@Serializable
sealed interface Gifted {
    /** "Gift a Food": the chosen player creates a Food token (702.174d). */
    @Serializable @SerialName("food") data object Food : Gifted
    /** "Gift a card": the chosen player draws a card (702.174e). */
    @Serializable @SerialName("card") data object Card : Gifted
    /** "Gift a tapped Fish": the chosen player creates a tapped 1/1 blue Fish creature token (702.174f). */
    @Serializable @SerialName("tapped-fish") data object TappedFish : Gifted
    /** "Gift an extra turn": the chosen player takes an extra turn after this one (702.174g). */
    @Serializable @SerialName("extra-turn") data object ExtraTurn : Gifted
    /** "Gift a Treasure": the chosen player creates a Treasure token (702.174h). */
    @Serializable @SerialName("treasure") data object Treasure : Gifted
    /** "Gift an Octopus": the chosen player creates an 8/8 blue Octopus creature token (702.174i). */
    @Serializable @SerialName("octopus") data object Octopus : Gifted
}

/** A keyword by name only, without values ("creatures with ward", "whenever you cycle"). Serialised as its [id]. */
interface KeywordKind {
    /** Stable name used in JSON ("flying", "crew"). */
    val id: String
    val text: String
    val rule: String

    companion object {
        /** Every keyword kind; a JVM test checks it matches the [Keyword] types. */
        val all: List<KeywordKind> by lazy {
            listOf(
                Keyword.Deathtouch,
                Keyword.Defender,
                Keyword.DoubleStrike,
                Keyword.FirstStrike,
                Keyword.Flash,
                Keyword.Flying,
                Keyword.Haste,
                Keyword.Hexproof,
                Keyword.Indestructible,
                Keyword.Intimidate,
                Keyword.Lifelink,
                Keyword.Reach,
                Keyword.Shroud,
                Keyword.Trample,
                Keyword.Vigilance,
                Keyword.Banding,
                Keyword.Flanking,
                Keyword.Phasing,
                Keyword.Shadow,
                Keyword.Horsemanship,
                Keyword.Fear,
                Keyword.Provoke,
                Keyword.Storm,
                Keyword.Sunburst,
                Keyword.Epic,
                Keyword.Convoke,
                Keyword.Haunt,
                Keyword.SplitSecond,
                Keyword.Delve,
                Keyword.Gravestorm,
                Keyword.Changeling,
                Keyword.Conspire,
                Keyword.Persist,
                Keyword.Wither,
                Keyword.Retrace,
                Keyword.Exalted,
                Keyword.Cascade,
                Keyword.Rebound,
                Keyword.UmbraArmor,
                Keyword.Infect,
                Keyword.BattleCry,
                Keyword.LivingWeapon,
                Keyword.Undying,
                Keyword.Soulbond,
                Keyword.Unleash,
                Keyword.Cipher,
                Keyword.Evolve,
                Keyword.Extort,
                Keyword.Fuse,
                Keyword.Dethrone,
                Keyword.HiddenAgenda,
                Keyword.Prowess,
                Keyword.Exploit,
                Keyword.Menace,
                Keyword.Devoid,
                Keyword.Ingest,
                Keyword.Myriad,
                Keyword.Skulk,
                Keyword.Melee,
                Keyword.Partner,
                Keyword.Undaunted,
                Keyword.Improvise,
                Keyword.Aftermath,
                Keyword.Ascend,
                Keyword.Assist,
                Keyword.JumpStart,
                Keyword.Mentor,
                Keyword.Riot,
                Keyword.Demonstrate,
                Keyword.Daybound,
                Keyword.Nightbound,
                Keyword.Decayed,
                Keyword.Training,
                Keyword.Compleated,
                Keyword.Enlist,
                Keyword.ReadAhead,
                Keyword.Ravenous,
                Keyword.LivingMetal,
                Keyword.ForMirrodin,
                Keyword.Bargain,
                Keyword.Spree,
                Keyword.StartYourEngines,
                Keyword.JobSelect,
                Keyword.Tiered,
                Keyword.Station,
                Keyword.Increment,
                Keyword.Paradigm,
                Keyword.Storied,
                Keyword.Rampage.Kind,
                Keyword.Fading.Kind,
                Keyword.Amplify.Kind,
                Keyword.Modular.Kind,
                Keyword.Bushido.Kind,
                Keyword.Soulshift.Kind,
                Keyword.Dredge.Kind,
                Keyword.Bloodthirst.Kind,
                Keyword.Graft.Kind,
                Keyword.Ripple.Kind,
                Keyword.Vanishing.Kind,
                Keyword.Absorb.Kind,
                Keyword.Frenzy.Kind,
                Keyword.Poisonous.Kind,
                Keyword.Hideaway.Kind,
                Keyword.Devour.Kind,
                Keyword.Annihilator.Kind,
                Keyword.Tribute.Kind,
                Keyword.Renown.Kind,
                Keyword.Crew.Kind,
                Keyword.Fabricate.Kind,
                Keyword.Afflict.Kind,
                Keyword.Afterlife.Kind,
                Keyword.Casualty.Kind,
                Keyword.Toxic.Kind,
                Keyword.Backup.Kind,
                Keyword.Saddle.Kind,
                Keyword.Mobilize.Kind,
                Keyword.Firebending.Kind,
                Keyword.Teamwork.Kind,
                Keyword.Ward.Kind,
                Keyword.CumulativeUpkeep.Kind,
                Keyword.Buyback.Kind,
                Keyword.Cycling.Kind,
                Keyword.Echo.Kind,
                Keyword.Flashback.Kind,
                Keyword.Madness.Kind,
                Keyword.Morph.Kind,
                Keyword.Entwine.Kind,
                Keyword.Ninjutsu.Kind,
                Keyword.Transmute.Kind,
                Keyword.Fortify.Kind,
                Keyword.Replicate.Kind,
                Keyword.Recover.Kind,
                Keyword.Evoke.Kind,
                Keyword.Prowl.Kind,
                Keyword.Unearth.Kind,
                Keyword.LevelUp.Kind,
                Keyword.Miracle.Kind,
                Keyword.Overload.Kind,
                Keyword.Scavenge.Kind,
                Keyword.Bestow.Kind,
                Keyword.Outlast.Kind,
                Keyword.Dash.Kind,
                Keyword.Surge.Kind,
                Keyword.Emerge.Kind,
                Keyword.Escalate.Kind,
                Keyword.Embalm.Kind,
                Keyword.Eternalize.Kind,
                Keyword.Spectacle.Kind,
                Keyword.Escape.Kind,
                Keyword.Mutate.Kind,
                Keyword.Encore.Kind,
                Keyword.Foretell.Kind,
                Keyword.Disturb.Kind,
                Keyword.Cleave.Kind,
                Keyword.Reconfigure.Kind,
                Keyword.Blitz.Kind,
                Keyword.Squad.Kind,
                Keyword.Disguise.Kind,
                Keyword.Plot.Kind,
                Keyword.Freerunning.Kind,
                Keyword.Offspring.Kind,
                Keyword.Harmonize.Kind,
                Keyword.Warp.Kind,
                Keyword.Mayhem.Kind,
                Keyword.WebSlinging.Kind,
                Keyword.Sneak.Kind,
                Keyword.Suspend.Kind,
                Keyword.Reinforce.Kind,
                Keyword.Awaken.Kind,
                Keyword.Impending.Kind,
                Keyword.Equip.Kind,
                Keyword.Kicker.Kind,
                Keyword.Enchant.Kind,
                Keyword.Protection.Kind,
                Keyword.Landwalk.Kind,
                Keyword.Affinity.Kind,
                Keyword.Splice.Kind,
                Keyword.PartnerWith.Kind,
                Keyword.CompanionAbility.Kind,
                Keyword.CommanderNinjutsu.Kind,
                Keyword.Prototype.Kind,
                Keyword.Gift.Kind,
                Keyword.ClassLevel.Kind,
            )
        }
        fun byId(id: String): KeywordKind? = all.firstOrNull { it.id == id }
    }
}

/** A keyword written with a number: "Crew 3". */
interface HasAmount { val amount: Int }

/** A keyword written with a cost: "Equip {2}", "Ward—Pay 3 life." */
interface HasCost { val cost: Cost }

private fun requireAmount(amount: Int) = require(amount >= 0) { "Keyword amount must not be negative: $amount" }

/** Writes a [KeywordKind] as its id. */
object KeywordKindSerializer : KSerializer<KeywordKind> {
    override val descriptor = PrimitiveSerialDescriptor("arcanum.api.KeywordKind", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: KeywordKind) = encoder.encodeString(value.id)
    override fun deserialize(decoder: Decoder): KeywordKind {
        val id = decoder.decodeString()
        return KeywordKind.byId(id) ?: throw IllegalArgumentException("Unknown keyword: $id")
    }
}
