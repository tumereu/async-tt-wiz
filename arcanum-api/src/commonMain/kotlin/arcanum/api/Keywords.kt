package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A keyword ability (CR 702) in its short, printed form, e.g. "Equip {2}". Each keyword is its own type carrying
 * exactly the values it is written with. Whether the engine treats it as a rules hook or expands it into an ordinary
 * ability is the engine's business; the definition only records what the card says. Keywords written with other
 * values (a quality, an object, a non-mana cost) are added when those values can be represented.
 */
@Serializable
sealed interface Keyword : Ability {
    /** Printed name, as at the start of an oracle line ("First strike"). */
    val text: String
    /** The rule defining it in the pinned rules version ([ComprehensiveRules]), e.g. "702.9". */
    val rule: String

    /** Deathtouch (CR 702.2) */
    @Serializable @SerialName("deathtouch") data object Deathtouch : Keyword { override val text: String get() = "Deathtouch"; override val rule: String get() = "702.2" }
    /** Defender (CR 702.3) */
    @Serializable @SerialName("defender") data object Defender : Keyword { override val text: String get() = "Defender"; override val rule: String get() = "702.3" }
    /** Double strike (CR 702.4) */
    @Serializable @SerialName("double-strike") data object DoubleStrike : Keyword { override val text: String get() = "Double strike"; override val rule: String get() = "702.4" }
    /** First strike (CR 702.7) */
    @Serializable @SerialName("first-strike") data object FirstStrike : Keyword { override val text: String get() = "First strike"; override val rule: String get() = "702.7" }
    /** Flash (CR 702.8) */
    @Serializable @SerialName("flash") data object Flash : Keyword { override val text: String get() = "Flash"; override val rule: String get() = "702.8" }
    /** Flying (CR 702.9) */
    @Serializable @SerialName("flying") data object Flying : Keyword { override val text: String get() = "Flying"; override val rule: String get() = "702.9" }
    /** Haste (CR 702.10) */
    @Serializable @SerialName("haste") data object Haste : Keyword { override val text: String get() = "Haste"; override val rule: String get() = "702.10" }
    /** Hexproof (CR 702.11) */
    @Serializable @SerialName("hexproof") data object Hexproof : Keyword { override val text: String get() = "Hexproof"; override val rule: String get() = "702.11" }
    /** Indestructible (CR 702.12) */
    @Serializable @SerialName("indestructible") data object Indestructible : Keyword { override val text: String get() = "Indestructible"; override val rule: String get() = "702.12" }
    /** Intimidate (CR 702.13) */
    @Serializable @SerialName("intimidate") data object Intimidate : Keyword { override val text: String get() = "Intimidate"; override val rule: String get() = "702.13" }
    /** Lifelink (CR 702.15) */
    @Serializable @SerialName("lifelink") data object Lifelink : Keyword { override val text: String get() = "Lifelink"; override val rule: String get() = "702.15" }
    /** Reach (CR 702.17) */
    @Serializable @SerialName("reach") data object Reach : Keyword { override val text: String get() = "Reach"; override val rule: String get() = "702.17" }
    /** Shroud (CR 702.18) */
    @Serializable @SerialName("shroud") data object Shroud : Keyword { override val text: String get() = "Shroud"; override val rule: String get() = "702.18" }
    /** Trample (CR 702.19) */
    @Serializable @SerialName("trample") data object Trample : Keyword { override val text: String get() = "Trample"; override val rule: String get() = "702.19" }
    /** Vigilance (CR 702.20) */
    @Serializable @SerialName("vigilance") data object Vigilance : Keyword { override val text: String get() = "Vigilance"; override val rule: String get() = "702.20" }
    /** Banding (CR 702.22) */
    @Serializable @SerialName("banding") data object Banding : Keyword { override val text: String get() = "Banding"; override val rule: String get() = "702.22" }
    /** Flanking (CR 702.25) */
    @Serializable @SerialName("flanking") data object Flanking : Keyword { override val text: String get() = "Flanking"; override val rule: String get() = "702.25" }
    /** Phasing (CR 702.26) */
    @Serializable @SerialName("phasing") data object Phasing : Keyword { override val text: String get() = "Phasing"; override val rule: String get() = "702.26" }
    /** Shadow (CR 702.28) */
    @Serializable @SerialName("shadow") data object Shadow : Keyword { override val text: String get() = "Shadow"; override val rule: String get() = "702.28" }
    /** Horsemanship (CR 702.31) */
    @Serializable @SerialName("horsemanship") data object Horsemanship : Keyword { override val text: String get() = "Horsemanship"; override val rule: String get() = "702.31" }
    /** Fear (CR 702.36) */
    @Serializable @SerialName("fear") data object Fear : Keyword { override val text: String get() = "Fear"; override val rule: String get() = "702.36" }
    /** Provoke (CR 702.39) */
    @Serializable @SerialName("provoke") data object Provoke : Keyword { override val text: String get() = "Provoke"; override val rule: String get() = "702.39" }
    /** Storm (CR 702.40) */
    @Serializable @SerialName("storm") data object Storm : Keyword { override val text: String get() = "Storm"; override val rule: String get() = "702.40" }
    /** Sunburst (CR 702.44) */
    @Serializable @SerialName("sunburst") data object Sunburst : Keyword { override val text: String get() = "Sunburst"; override val rule: String get() = "702.44" }
    /** Epic (CR 702.50) */
    @Serializable @SerialName("epic") data object Epic : Keyword { override val text: String get() = "Epic"; override val rule: String get() = "702.50" }
    /** Convoke (CR 702.51) */
    @Serializable @SerialName("convoke") data object Convoke : Keyword { override val text: String get() = "Convoke"; override val rule: String get() = "702.51" }
    /** Haunt (CR 702.55) */
    @Serializable @SerialName("haunt") data object Haunt : Keyword { override val text: String get() = "Haunt"; override val rule: String get() = "702.55" }
    /** Split second (CR 702.61) */
    @Serializable @SerialName("split-second") data object SplitSecond : Keyword { override val text: String get() = "Split second"; override val rule: String get() = "702.61" }
    /** Delve (CR 702.66) */
    @Serializable @SerialName("delve") data object Delve : Keyword { override val text: String get() = "Delve"; override val rule: String get() = "702.66" }
    /** Gravestorm (CR 702.69) */
    @Serializable @SerialName("gravestorm") data object Gravestorm : Keyword { override val text: String get() = "Gravestorm"; override val rule: String get() = "702.69" }
    /** Changeling (CR 702.73) */
    @Serializable @SerialName("changeling") data object Changeling : Keyword { override val text: String get() = "Changeling"; override val rule: String get() = "702.73" }
    /** Conspire (CR 702.78) */
    @Serializable @SerialName("conspire") data object Conspire : Keyword { override val text: String get() = "Conspire"; override val rule: String get() = "702.78" }
    /** Persist (CR 702.79) */
    @Serializable @SerialName("persist") data object Persist : Keyword { override val text: String get() = "Persist"; override val rule: String get() = "702.79" }
    /** Wither (CR 702.80) */
    @Serializable @SerialName("wither") data object Wither : Keyword { override val text: String get() = "Wither"; override val rule: String get() = "702.80" }
    /** Retrace (CR 702.81) */
    @Serializable @SerialName("retrace") data object Retrace : Keyword { override val text: String get() = "Retrace"; override val rule: String get() = "702.81" }
    /** Exalted (CR 702.83) */
    @Serializable @SerialName("exalted") data object Exalted : Keyword { override val text: String get() = "Exalted"; override val rule: String get() = "702.83" }
    /** Cascade (CR 702.85) */
    @Serializable @SerialName("cascade") data object Cascade : Keyword { override val text: String get() = "Cascade"; override val rule: String get() = "702.85" }
    /** Rebound (CR 702.88) */
    @Serializable @SerialName("rebound") data object Rebound : Keyword { override val text: String get() = "Rebound"; override val rule: String get() = "702.88" }
    /** Umbra armor (CR 702.89) */
    @Serializable @SerialName("umbra-armor") data object UmbraArmor : Keyword { override val text: String get() = "Umbra armor"; override val rule: String get() = "702.89" }
    /** Infect (CR 702.90) */
    @Serializable @SerialName("infect") data object Infect : Keyword { override val text: String get() = "Infect"; override val rule: String get() = "702.90" }
    /** Battle cry (CR 702.91) */
    @Serializable @SerialName("battle-cry") data object BattleCry : Keyword { override val text: String get() = "Battle cry"; override val rule: String get() = "702.91" }
    /** Living weapon (CR 702.92) */
    @Serializable @SerialName("living-weapon") data object LivingWeapon : Keyword { override val text: String get() = "Living weapon"; override val rule: String get() = "702.92" }
    /** Undying (CR 702.93) */
    @Serializable @SerialName("undying") data object Undying : Keyword { override val text: String get() = "Undying"; override val rule: String get() = "702.93" }
    /** Soulbond (CR 702.95) */
    @Serializable @SerialName("soulbond") data object Soulbond : Keyword { override val text: String get() = "Soulbond"; override val rule: String get() = "702.95" }
    /** Unleash (CR 702.98) */
    @Serializable @SerialName("unleash") data object Unleash : Keyword { override val text: String get() = "Unleash"; override val rule: String get() = "702.98" }
    /** Cipher (CR 702.99) */
    @Serializable @SerialName("cipher") data object Cipher : Keyword { override val text: String get() = "Cipher"; override val rule: String get() = "702.99" }
    /** Evolve (CR 702.100) */
    @Serializable @SerialName("evolve") data object Evolve : Keyword { override val text: String get() = "Evolve"; override val rule: String get() = "702.100" }
    /** Extort (CR 702.101) */
    @Serializable @SerialName("extort") data object Extort : Keyword { override val text: String get() = "Extort"; override val rule: String get() = "702.101" }
    /** Fuse (CR 702.102) */
    @Serializable @SerialName("fuse") data object Fuse : Keyword { override val text: String get() = "Fuse"; override val rule: String get() = "702.102" }
    /** Dethrone (CR 702.105) */
    @Serializable @SerialName("dethrone") data object Dethrone : Keyword { override val text: String get() = "Dethrone"; override val rule: String get() = "702.105" }
    /** Hidden agenda (CR 702.106) */
    @Serializable @SerialName("hidden-agenda") data object HiddenAgenda : Keyword { override val text: String get() = "Hidden agenda"; override val rule: String get() = "702.106" }
    /** Prowess (CR 702.108) */
    @Serializable @SerialName("prowess") data object Prowess : Keyword { override val text: String get() = "Prowess"; override val rule: String get() = "702.108" }
    /** Exploit (CR 702.110) */
    @Serializable @SerialName("exploit") data object Exploit : Keyword { override val text: String get() = "Exploit"; override val rule: String get() = "702.110" }
    /** Menace (CR 702.111) */
    @Serializable @SerialName("menace") data object Menace : Keyword { override val text: String get() = "Menace"; override val rule: String get() = "702.111" }
    /** Devoid (CR 702.114) */
    @Serializable @SerialName("devoid") data object Devoid : Keyword { override val text: String get() = "Devoid"; override val rule: String get() = "702.114" }
    /** Ingest (CR 702.115) */
    @Serializable @SerialName("ingest") data object Ingest : Keyword { override val text: String get() = "Ingest"; override val rule: String get() = "702.115" }
    /** Myriad (CR 702.116) */
    @Serializable @SerialName("myriad") data object Myriad : Keyword { override val text: String get() = "Myriad"; override val rule: String get() = "702.116" }
    /** Skulk (CR 702.118) */
    @Serializable @SerialName("skulk") data object Skulk : Keyword { override val text: String get() = "Skulk"; override val rule: String get() = "702.118" }
    /** Melee (CR 702.121) */
    @Serializable @SerialName("melee") data object Melee : Keyword { override val text: String get() = "Melee"; override val rule: String get() = "702.121" }
    /** Partner (CR 702.124) */
    @Serializable @SerialName("partner") data object Partner : Keyword { override val text: String get() = "Partner"; override val rule: String get() = "702.124" }
    /** Undaunted (CR 702.125) */
    @Serializable @SerialName("undaunted") data object Undaunted : Keyword { override val text: String get() = "Undaunted"; override val rule: String get() = "702.125" }
    /** Improvise (CR 702.126) */
    @Serializable @SerialName("improvise") data object Improvise : Keyword { override val text: String get() = "Improvise"; override val rule: String get() = "702.126" }
    /** Aftermath (CR 702.127) */
    @Serializable @SerialName("aftermath") data object Aftermath : Keyword { override val text: String get() = "Aftermath"; override val rule: String get() = "702.127" }
    /** Ascend (CR 702.131) */
    @Serializable @SerialName("ascend") data object Ascend : Keyword { override val text: String get() = "Ascend"; override val rule: String get() = "702.131" }
    /** Assist (CR 702.132) */
    @Serializable @SerialName("assist") data object Assist : Keyword { override val text: String get() = "Assist"; override val rule: String get() = "702.132" }
    /** Jump-start (CR 702.133) */
    @Serializable @SerialName("jump-start") data object JumpStart : Keyword { override val text: String get() = "Jump-start"; override val rule: String get() = "702.133" }
    /** Mentor (CR 702.134) */
    @Serializable @SerialName("mentor") data object Mentor : Keyword { override val text: String get() = "Mentor"; override val rule: String get() = "702.134" }
    /** Riot (CR 702.136) */
    @Serializable @SerialName("riot") data object Riot : Keyword { override val text: String get() = "Riot"; override val rule: String get() = "702.136" }
    /** Demonstrate (CR 702.144) */
    @Serializable @SerialName("demonstrate") data object Demonstrate : Keyword { override val text: String get() = "Demonstrate"; override val rule: String get() = "702.144" }
    /** Daybound (CR 702.145b) */
    @Serializable @SerialName("daybound") data object Daybound : Keyword { override val text: String get() = "Daybound"; override val rule: String get() = "702.145b" }
    /** Nightbound (CR 702.145e) */
    @Serializable @SerialName("nightbound") data object Nightbound : Keyword { override val text: String get() = "Nightbound"; override val rule: String get() = "702.145e" }
    /** Decayed (CR 702.147) */
    @Serializable @SerialName("decayed") data object Decayed : Keyword { override val text: String get() = "Decayed"; override val rule: String get() = "702.147" }
    /** Training (CR 702.149) */
    @Serializable @SerialName("training") data object Training : Keyword { override val text: String get() = "Training"; override val rule: String get() = "702.149" }
    /** Compleated (CR 702.150) */
    @Serializable @SerialName("compleated") data object Compleated : Keyword { override val text: String get() = "Compleated"; override val rule: String get() = "702.150" }
    /** Enlist (CR 702.154) */
    @Serializable @SerialName("enlist") data object Enlist : Keyword { override val text: String get() = "Enlist"; override val rule: String get() = "702.154" }
    /** Read ahead (CR 702.155) */
    @Serializable @SerialName("read-ahead") data object ReadAhead : Keyword { override val text: String get() = "Read ahead"; override val rule: String get() = "702.155" }
    /** Ravenous (CR 702.156) */
    @Serializable @SerialName("ravenous") data object Ravenous : Keyword { override val text: String get() = "Ravenous"; override val rule: String get() = "702.156" }
    /** Living metal (CR 702.161) */
    @Serializable @SerialName("living-metal") data object LivingMetal : Keyword { override val text: String get() = "Living metal"; override val rule: String get() = "702.161" }
    /** For Mirrodin! (CR 702.163) */
    @Serializable @SerialName("for-mirrodin") data object ForMirrodin : Keyword { override val text: String get() = "For Mirrodin!"; override val rule: String get() = "702.163" }
    /** Bargain (CR 702.166) */
    @Serializable @SerialName("bargain") data object Bargain : Keyword { override val text: String get() = "Bargain"; override val rule: String get() = "702.166" }
    /** Spree (CR 702.172) */
    @Serializable @SerialName("spree") data object Spree : Keyword { override val text: String get() = "Spree"; override val rule: String get() = "702.172" }
    /** Start your engines! (CR 702.179) */
    @Serializable @SerialName("start-your-engines") data object StartYourEngines : Keyword { override val text: String get() = "Start your engines!"; override val rule: String get() = "702.179" }
    /** Job select (CR 702.182) */
    @Serializable @SerialName("job-select") data object JobSelect : Keyword { override val text: String get() = "Job select"; override val rule: String get() = "702.182" }
    /** Tiered (CR 702.183) */
    @Serializable @SerialName("tiered") data object Tiered : Keyword { override val text: String get() = "Tiered"; override val rule: String get() = "702.183" }
    /** Station (CR 702.184) */
    @Serializable @SerialName("station") data object Station : Keyword { override val text: String get() = "Station"; override val rule: String get() = "702.184" }
    /** Increment (CR 702.191) */
    @Serializable @SerialName("increment") data object Increment : Keyword { override val text: String get() = "Increment"; override val rule: String get() = "702.191" }
    /** Storied (CR 702.195) */
    @Serializable @SerialName("storied") data object Storied : Keyword { override val text: String get() = "Storied"; override val rule: String get() = "702.195" }
    /** Rampage (CR 702.23) */
    @Serializable @SerialName("rampage") data class Rampage(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Rampage"; override val rule: String get() = "702.23"
    }
    /** Fading (CR 702.32) */
    @Serializable @SerialName("fading") data class Fading(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Fading"; override val rule: String get() = "702.32"
    }
    /** Amplify (CR 702.38) */
    @Serializable @SerialName("amplify") data class Amplify(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Amplify"; override val rule: String get() = "702.38"
    }
    /** Modular (CR 702.43) */
    @Serializable @SerialName("modular") data class Modular(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Modular"; override val rule: String get() = "702.43"
    }
    /** Bushido (CR 702.45) */
    @Serializable @SerialName("bushido") data class Bushido(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Bushido"; override val rule: String get() = "702.45"
    }
    /** Soulshift (CR 702.46) */
    @Serializable @SerialName("soulshift") data class Soulshift(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Soulshift"; override val rule: String get() = "702.46"
    }
    /** Dredge (CR 702.52) */
    @Serializable @SerialName("dredge") data class Dredge(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Dredge"; override val rule: String get() = "702.52"
    }
    /** Bloodthirst (CR 702.54) */
    @Serializable @SerialName("bloodthirst") data class Bloodthirst(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Bloodthirst"; override val rule: String get() = "702.54"
    }
    /** Graft (CR 702.58) */
    @Serializable @SerialName("graft") data class Graft(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Graft"; override val rule: String get() = "702.58"
    }
    /** Ripple (CR 702.60) */
    @Serializable @SerialName("ripple") data class Ripple(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Ripple"; override val rule: String get() = "702.60"
    }
    /** Vanishing (CR 702.63) */
    @Serializable @SerialName("vanishing") data class Vanishing(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Vanishing"; override val rule: String get() = "702.63"
    }
    /** Absorb (CR 702.64) */
    @Serializable @SerialName("absorb") data class Absorb(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Absorb"; override val rule: String get() = "702.64"
    }
    /** Frenzy (CR 702.68) */
    @Serializable @SerialName("frenzy") data class Frenzy(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Frenzy"; override val rule: String get() = "702.68"
    }
    /** Poisonous (CR 702.70) */
    @Serializable @SerialName("poisonous") data class Poisonous(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Poisonous"; override val rule: String get() = "702.70"
    }
    /** Hideaway (CR 702.75) */
    @Serializable @SerialName("hideaway") data class Hideaway(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Hideaway"; override val rule: String get() = "702.75"
    }
    /** Devour (CR 702.82) */
    @Serializable @SerialName("devour") data class Devour(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Devour"; override val rule: String get() = "702.82"
    }
    /** Annihilator (CR 702.86) */
    @Serializable @SerialName("annihilator") data class Annihilator(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Annihilator"; override val rule: String get() = "702.86"
    }
    /** Tribute (CR 702.104) */
    @Serializable @SerialName("tribute") data class Tribute(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Tribute"; override val rule: String get() = "702.104"
    }
    /** Renown (CR 702.112) */
    @Serializable @SerialName("renown") data class Renown(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Renown"; override val rule: String get() = "702.112"
    }
    /** Crew (CR 702.122) */
    @Serializable @SerialName("crew") data class Crew(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Crew"; override val rule: String get() = "702.122"
    }
    /** Fabricate (CR 702.123) */
    @Serializable @SerialName("fabricate") data class Fabricate(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Fabricate"; override val rule: String get() = "702.123"
    }
    /** Afflict (CR 702.130) */
    @Serializable @SerialName("afflict") data class Afflict(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Afflict"; override val rule: String get() = "702.130"
    }
    /** Afterlife (CR 702.135) */
    @Serializable @SerialName("afterlife") data class Afterlife(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Afterlife"; override val rule: String get() = "702.135"
    }
    /** Casualty (CR 702.153) */
    @Serializable @SerialName("casualty") data class Casualty(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Casualty"; override val rule: String get() = "702.153"
    }
    /** Toxic (CR 702.164) */
    @Serializable @SerialName("toxic") data class Toxic(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Toxic"; override val rule: String get() = "702.164"
    }
    /** Backup (CR 702.165) */
    @Serializable @SerialName("backup") data class Backup(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Backup"; override val rule: String get() = "702.165"
    }
    /** Saddle (CR 702.171) */
    @Serializable @SerialName("saddle") data class Saddle(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Saddle"; override val rule: String get() = "702.171"
    }
    /** Mobilize (CR 702.181) */
    @Serializable @SerialName("mobilize") data class Mobilize(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Mobilize"; override val rule: String get() = "702.181"
    }
    /** Firebending (CR 702.189) */
    @Serializable @SerialName("firebending") data class Firebending(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Firebending"; override val rule: String get() = "702.189"
    }
    /** Teamwork (CR 702.194) */
    @Serializable @SerialName("teamwork") data class Teamwork(override val amount: Int) : Keyword, HasAmount {
        init { requireAmount(amount) }
        override val text: String get() = "Teamwork"; override val rule: String get() = "702.194"
    }
    /** Equip (CR 702.6) */
    @Serializable @SerialName("equip") data class Equip(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Equip"; override val rule: String get() = "702.6" }
    /** Ward (CR 702.21) */
    @Serializable @SerialName("ward") data class Ward(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Ward"; override val rule: String get() = "702.21" }
    /** Cumulative upkeep (CR 702.24) */
    @Serializable @SerialName("cumulative-upkeep") data class CumulativeUpkeep(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Cumulative upkeep"; override val rule: String get() = "702.24" }
    /** Buyback (CR 702.27) */
    @Serializable @SerialName("buyback") data class Buyback(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Buyback"; override val rule: String get() = "702.27" }
    /** Cycling (CR 702.29) */
    @Serializable @SerialName("cycling") data class Cycling(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Cycling"; override val rule: String get() = "702.29" }
    /** Echo (CR 702.30) */
    @Serializable @SerialName("echo") data class Echo(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Echo"; override val rule: String get() = "702.30" }
    /** Kicker (CR 702.33) */
    @Serializable @SerialName("kicker") data class Kicker(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Kicker"; override val rule: String get() = "702.33" }
    /** Multikicker (CR 702.33c) */
    @Serializable @SerialName("multikicker") data class Multikicker(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Multikicker"; override val rule: String get() = "702.33c" }
    /** Flashback (CR 702.34) */
    @Serializable @SerialName("flashback") data class Flashback(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Flashback"; override val rule: String get() = "702.34" }
    /** Madness (CR 702.35) */
    @Serializable @SerialName("madness") data class Madness(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Madness"; override val rule: String get() = "702.35" }
    /** Morph (CR 702.37) */
    @Serializable @SerialName("morph") data class Morph(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Morph"; override val rule: String get() = "702.37" }
    /** Megamorph (CR 702.37b) */
    @Serializable @SerialName("megamorph") data class Megamorph(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Megamorph"; override val rule: String get() = "702.37b" }
    /** Entwine (CR 702.42) */
    @Serializable @SerialName("entwine") data class Entwine(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Entwine"; override val rule: String get() = "702.42" }
    /** Ninjutsu (CR 702.49) */
    @Serializable @SerialName("ninjutsu") data class Ninjutsu(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Ninjutsu"; override val rule: String get() = "702.49" }
    /** Transmute (CR 702.53) */
    @Serializable @SerialName("transmute") data class Transmute(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Transmute"; override val rule: String get() = "702.53" }
    /** Fortify (CR 702.67) */
    @Serializable @SerialName("fortify") data class Fortify(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Fortify"; override val rule: String get() = "702.67" }
    /** Replicate (CR 702.56) */
    @Serializable @SerialName("replicate") data class Replicate(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Replicate"; override val rule: String get() = "702.56" }
    /** Recover (CR 702.59) */
    @Serializable @SerialName("recover") data class Recover(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Recover"; override val rule: String get() = "702.59" }
    /** Evoke (CR 702.74) */
    @Serializable @SerialName("evoke") data class Evoke(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Evoke"; override val rule: String get() = "702.74" }
    /** Prowl (CR 702.76) */
    @Serializable @SerialName("prowl") data class Prowl(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Prowl"; override val rule: String get() = "702.76" }
    /** Unearth (CR 702.84) */
    @Serializable @SerialName("unearth") data class Unearth(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Unearth"; override val rule: String get() = "702.84" }
    /** Level up (CR 702.87) */
    @Serializable @SerialName("level-up") data class LevelUp(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Level up"; override val rule: String get() = "702.87" }
    /** Miracle (CR 702.94) */
    @Serializable @SerialName("miracle") data class Miracle(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Miracle"; override val rule: String get() = "702.94" }
    /** Overload (CR 702.96) */
    @Serializable @SerialName("overload") data class Overload(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Overload"; override val rule: String get() = "702.96" }
    /** Scavenge (CR 702.97) */
    @Serializable @SerialName("scavenge") data class Scavenge(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Scavenge"; override val rule: String get() = "702.97" }
    /** Bestow (CR 702.103) */
    @Serializable @SerialName("bestow") data class Bestow(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Bestow"; override val rule: String get() = "702.103" }
    /** Outlast (CR 702.107) */
    @Serializable @SerialName("outlast") data class Outlast(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Outlast"; override val rule: String get() = "702.107" }
    /** Dash (CR 702.109) */
    @Serializable @SerialName("dash") data class Dash(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Dash"; override val rule: String get() = "702.109" }
    /** Surge (CR 702.117) */
    @Serializable @SerialName("surge") data class Surge(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Surge"; override val rule: String get() = "702.117" }
    /** Emerge (CR 702.119) */
    @Serializable @SerialName("emerge") data class Emerge(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Emerge"; override val rule: String get() = "702.119" }
    /** Escalate (CR 702.120) */
    @Serializable @SerialName("escalate") data class Escalate(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Escalate"; override val rule: String get() = "702.120" }
    /** Embalm (CR 702.128) */
    @Serializable @SerialName("embalm") data class Embalm(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Embalm"; override val rule: String get() = "702.128" }
    /** Eternalize (CR 702.129) */
    @Serializable @SerialName("eternalize") data class Eternalize(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Eternalize"; override val rule: String get() = "702.129" }
    /** Spectacle (CR 702.137) */
    @Serializable @SerialName("spectacle") data class Spectacle(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Spectacle"; override val rule: String get() = "702.137" }
    /** Escape (CR 702.138) */
    @Serializable @SerialName("escape") data class Escape(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Escape"; override val rule: String get() = "702.138" }
    /** Mutate (CR 702.140) */
    @Serializable @SerialName("mutate") data class Mutate(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Mutate"; override val rule: String get() = "702.140" }
    /** Encore (CR 702.141) */
    @Serializable @SerialName("encore") data class Encore(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Encore"; override val rule: String get() = "702.141" }
    /** Foretell (CR 702.143) */
    @Serializable @SerialName("foretell") data class Foretell(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Foretell"; override val rule: String get() = "702.143" }
    /** Disturb (CR 702.146) */
    @Serializable @SerialName("disturb") data class Disturb(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Disturb"; override val rule: String get() = "702.146" }
    /** Cleave (CR 702.148) */
    @Serializable @SerialName("cleave") data class Cleave(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Cleave"; override val rule: String get() = "702.148" }
    /** Reconfigure (CR 702.151) */
    @Serializable @SerialName("reconfigure") data class Reconfigure(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Reconfigure"; override val rule: String get() = "702.151" }
    /** Blitz (CR 702.152) */
    @Serializable @SerialName("blitz") data class Blitz(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Blitz"; override val rule: String get() = "702.152" }
    /** Squad (CR 702.157) */
    @Serializable @SerialName("squad") data class Squad(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Squad"; override val rule: String get() = "702.157" }
    /** Disguise (CR 702.168) */
    @Serializable @SerialName("disguise") data class Disguise(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Disguise"; override val rule: String get() = "702.168" }
    /** Plot (CR 702.170) */
    @Serializable @SerialName("plot") data class Plot(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Plot"; override val rule: String get() = "702.170" }
    /** Freerunning (CR 702.173) */
    @Serializable @SerialName("freerunning") data class Freerunning(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Freerunning"; override val rule: String get() = "702.173" }
    /** Offspring (CR 702.175) */
    @Serializable @SerialName("offspring") data class Offspring(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Offspring"; override val rule: String get() = "702.175" }
    /** Harmonize (CR 702.180) */
    @Serializable @SerialName("harmonize") data class Harmonize(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Harmonize"; override val rule: String get() = "702.180" }
    /** Warp (CR 702.185) */
    @Serializable @SerialName("warp") data class Warp(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Warp"; override val rule: String get() = "702.185" }
    /** Mayhem (CR 702.187) */
    @Serializable @SerialName("mayhem") data class Mayhem(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Mayhem"; override val rule: String get() = "702.187" }
    /** Web-slinging (CR 702.188) */
    @Serializable @SerialName("web-slinging") data class WebSlinging(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Web-slinging"; override val rule: String get() = "702.188" }
    /** Sneak (CR 702.190) */
    @Serializable @SerialName("sneak") data class Sneak(override val cost: ManaCost) : Keyword, HasCost { override val text: String get() = "Sneak"; override val rule: String get() = "702.190" }
    /** Suspend (CR 702.62) */
    @Serializable @SerialName("suspend") data class Suspend(override val amount: Int, override val cost: ManaCost) : Keyword, HasAmount, HasCost {
        init { requireAmount(amount) }
        override val text: String get() = "Suspend"; override val rule: String get() = "702.62"
    }
    /** Reinforce (CR 702.77) */
    @Serializable @SerialName("reinforce") data class Reinforce(override val amount: Int, override val cost: ManaCost) : Keyword, HasAmount, HasCost {
        init { requireAmount(amount) }
        override val text: String get() = "Reinforce"; override val rule: String get() = "702.77"
    }
    /** Awaken (CR 702.113) */
    @Serializable @SerialName("awaken") data class Awaken(override val amount: Int, override val cost: ManaCost) : Keyword, HasAmount, HasCost {
        init { requireAmount(amount) }
        override val text: String get() = "Awaken"; override val rule: String get() = "702.113"
    }
    /** Impending (CR 702.176) */
    @Serializable @SerialName("impending") data class Impending(override val amount: Int, override val cost: ManaCost) : Keyword, HasAmount, HasCost {
        init { requireAmount(amount) }
        override val text: String get() = "Impending"; override val rule: String get() = "702.176"
    }
}

/** A keyword written with a number: "Crew 3". */
sealed interface HasAmount { val amount: Int }

/** A keyword written with a mana cost: "Equip {2}". */
sealed interface HasCost { val cost: ManaCost }

private fun requireAmount(amount: Int) = require(amount >= 0) { "Keyword amount must not be negative: $amount" }
