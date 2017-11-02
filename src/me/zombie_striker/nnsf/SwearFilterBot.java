package me.zombie_striker.nnsf;

/**
 Copyright (C) 2017  Zombie_Striker

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.
 **/

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import me.zombie_striker.neuralnetwork.Controler;
import me.zombie_striker.neuralnetwork.NNAI;
import me.zombie_striker.neuralnetwork.NNBaseEntity;
import me.zombie_striker.neuralnetwork.neurons.BiasNeuron;
import me.zombie_striker.neuralnetwork.neurons.Neuron;
import me.zombie_striker.neuralnetwork.neurons.input.InputLetterNeuron;
import me.zombie_striker.neuralnetwork.neurons.input.InputNeuron;
import me.zombie_striker.neuralnetwork.senses.Sensory2D_Letters;
import me.zombie_striker.neuralnetwork.util.DeepReinforcementUtil;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class SwearFilterBot extends NNBaseEntity implements Controler {

	public static char[] letters = InputLetterNeuron.letters;
	// Returns capital letters A-Z and 0-9

	public Sensory2D_Letters word = new Sensory2D_Letters("none");
	public boolean wasCorrect = true;

	public static List<String> cleanWords = new ArrayList<String>();
	public List<String> swearWords = new ArrayList<String>();

	public String filterType = "null";
	public int filterid = 0;

	public static String[] swearlist = { "fuck", "shit", "bitch", "cunt",
			"fag", "dick", "twat", "penis", "vagina", "nigger", "tits",
			"pussy", "whore", "slut", "cum", "gay", "vaj" };

	@Override
	public Map<String, Object> serialize() {
		Map<String, Object> map = super.serialize();
		map.put("filterid", filterid);
		return map;
	}

	public SwearFilterBot(Map<String, Object> map) {
		super(map);
		int id = (int) map.get("filterid");
		filterType = swearlist[id];
		initValidNames(id);
		filterid = id;
		this.word = (Sensory2D_Letters) ((InputNeuron) ai.getLayer(0).neuronsInLayer
				.get(0)).getSenses();
	}

	public SwearFilterBot(boolean createAI, int id) {
		super(false, 2000);
		/**
		 * The second value for the super is how many points are stored for
		 * accuracy. This means that the accuracy printed
		 */
		if (createAI) {
			// Generates an ai with ONE output, which is equal to whether it is
			// a player
			this.ai = NNAI.generateAI(this, 1, 4, "Is a swear word");

			for (int index = 0; index < 16; index++) {
				for (int character = 0; character < letters.length; character++) {
					// 1st one is what index, the next is the actual character
					InputLetterNeuron.generateNeuronStatically(ai, index,
							character, this.word);
				}
			}
			/**
			 * Two layers are used for this one because there are a lot of
			 * nuances in language. Adding more hidden layers will increase the
			 * amount of variance and specificity that it will look for.
			 */
			// Creates the neurons for layer 1.
			for (int neurons = 0; neurons < 40; neurons++)
				Neuron.generateNeuronStatically(ai, 1);
			// Creates the neurons for layer 2
			for (int neurons = 0; neurons < 15; neurons++)
				Neuron.generateNeuronStatically(ai, 2);

			BiasNeuron.generateNeuronStatically(ai, 0);
			BiasNeuron.generateNeuronStatically(ai, 1);

			connectNeurons();
		}
		this.controler = this;

		this.setNeuronsPerRow(0, letters.length);

		/**
		 * Since there is no universal quality in the arrangement of all 5 swear
		 * words, each NN has to be trained for each specific swear word.
		 */
		filterType = swearlist[id];
		initValidNames(id);
		filterid = id;
	}

	@Override
	public String update() {
		if (shouldLearn) {
			boolean useSwear = ThreadLocalRandom.current().nextBoolean()
					&& ThreadLocalRandom.current().nextBoolean();
			// The booleans are doubled to make sure swear words happen 1/4 the
			// time, as it is more important to make sure clean words are not
			// flagged than to go over swear words.
			if (useSwear) {
				word.changeWord((String) swearWords.toArray()[(int) ((swearWords
						.size() - 1) * Math.random())]);
			} else {
				word.changeWord((String) cleanWords.toArray()[(int) ((cleanWords
						.size() - 1) * Math.random())]);
			}
		}
		boolean result = tickAndThink()[0];

		if (!shouldLearn) {
			return "" + result;

		} else {
			boolean isswear = swearWords.contains(word.getWord());
			wasCorrect = result == isswear;
			// Returns if it was a swear word;

			this.getAccuracy().addEntry(wasCorrect);
			float accuracy = (float) this.getAccuracy().getAccuracy();

			// IMPROVE IT
			Neuron[] array = new Neuron[1];
			if (isswear)
				array[0] = ai.getNeuronFromId(0);
			// Instead of hashmaps, since there is only 1 output neuron, is is
			// easier to do this instead of defining the suggested value.
			// Adding it to the array means the suggested value is +1. Not
			// having it means it is -1.

			// only learn when it was not correct.
			if (!wasCorrect) {
				DeepReinforcementUtil.instantaneousReinforce(this, array, 1);
			}
			return ((wasCorrect ? ChatColor.GREEN : ChatColor.RED) + "acc "
					+ ((int) (100 * accuracy)) + "|=" + word.getWord() + "|  "
					+ result + "." + isswear + "|Swear-Score " + ((int) (100 * (ai
					.getNeuronFromId(0).getTriggeredStength()))));
		}
	}

	@Override
	public void setInputs(CommandSender initiator, String[] args) {
		if (this.shouldLearn) {
			initiator
					.sendMessage("Stop the learning before testing. use /nn stoplearning");
			return;
		}
		if (args.length > 1) {
			String username = "  " + args[1].toUpperCase();
			this.word.changeWord(username);
			return;
		} else {
			initiator.sendMessage("Provide an id");
		}
	}

	@Override
	public NNBaseEntity clone() {
		SwearFilterBot thi = new SwearFilterBot(false, filterid);
		thi.ai = this.ai;
		return thi;
	}

	public void setBase(NNBaseEntity t) {
		// this.base = (SwearBot) t;
	}

	/**
	 * Adds string B to the hashmap with value true
	 * 
	 * @param b
	 */
	public void a(boolean d, String... b) {
		if (d) {
			for (String c : b)
				swearWords.add(c.toUpperCase());
		} else {
			for (String c : b)
				cleanWords.add(c.toUpperCase());
			// isSwearWord.put(c.toUpperCase(), d);
		}
	}

	public void a(boolean d, String full) {
		full = full.toUpperCase().replaceAll(" ", "").replaceAll("!", "")
				.replaceAll("\\?", "").replaceAll(".", "").replaceAll(",", "")
				.replaceAll("'", "").replaceAll("\"", "").replaceAll("-", "");
		int skip = 0;
		for (; skip > full.length();) {
			int size = (int) ((Math.random() * 13) + 2);
			String c = full.substring(skip, size);
			skip += size;
			if (d) {
				swearWords.add(c);
			} else {
				cleanWords.add(c);
			}
			// isSwearWord.put(c, d);
		}
	}

	private void initValidNames(int i) {

		// swears
		/**
		 * Because there is not some universal element that exists for all swear
		 * words in existance, you have to narrow the search to just one swear
		 * word and its varients. For each swear word you want to filter out,
		 * you need to create and train a new NN
		 */
		if (i == 0)
			a(true, "  fuck", "  fuk", "  fuc", "  fck", "  phuc", "  phuk",
					"  fuck", "  fuuuuck", "  fook", "  fuuuk", "  fuka",
					"  fuck", "  fuck", "  fook", "  foock", "  fooook",
					"  fuuk", "  fuc", "  fuuuk", "  fuuuuck", "childfuck",
					"motherfuck");
		if (i == 1)
			a(true, "  shit", "  shhit", "  shhhiiiit", "  shit", "  shiiit",
					"  shhhhhhhiiiiiiit", "  shitty", "  shat", "  shaaaat",
					"  shart", "  shaaaaart", "  shitt", "  shiiit");
		if (i == 2)
			a(true, "  bitch", "  bich", "  bitch", "  btch", "  biiiitch",
					"  biiiiich", "  biatch", "  biiiaaatch", "  biiiatch",
					"  bitttttttttch", "  biiiiiiich", "  bicccch", "  bitch");
		if (i == 3)
			a(true, "  cunt", "  cuuuuuunt", "  kunt", "  cuuuuuuuunnnt",
					"  cuuunt", "  kuuuunnt", "  kunnnnt");
		if (i == 4)
			a(true, "  fag", "  faggot", "  fagget", "  feggit", "  figgit",
					"  faaaaag", "  phagot", "  phaggot", "  phag",
					"  phaaaaag", "  phaaaget", "  faaaaagggot", "  phegot",
					"  pheggot");
		if (i == 5)
			a(true, "  dick", "  diiiiiick", "  diccccccck", "  dick",
					"  d1ck", " dck");
		if (i == 6)
			a(true, " twat", "  twaaaaaat", " twwwwwwwwaat");
		if (i == 7)
			a(true, "  penis", "  peeeeenis", "  pennnnnnnnnnis",
					"  peniiiiiiiiiis");
		if (i == 8)
			a(true, "  vag", "  vaaaaag", "   vagiiiiiiiina", "  vaginnnnna");
		if (i == 9)
			a(true, "  nigger", "  nigggger", "  niiiiiiger", "  nigaaaaaaar",
					"  niiiigggeeeeer", "  nigga", "  nega");
		if (i == 10)
			a(true, "  tits", "  tiiits", "  taatas", "  tatas",
					"tiiiiiiiiits", "tiiiiits", "taaataas", "taaaaatttaaaas");
		if (i == 11)
			a(true, "  pussy", "  puuuuusssy", "  puss", "puuuuuuuuss");
		if (i == 12)
			a(true, "  whore", "  whhhhhooooor", "  whhhooor", "  whhor",
					"  whooor", "  whoor", "  whhhoooor", "  whhhoooooooor");
		if (i == 13)
			a(true, "  slut", "  slllut", "  sllluuut", "  sllut",
					"  slllllluuuut", "  sluuuuut");
		if (i == 14)
			a(true, "  cum", "  cuuuuuum", "  cuuum", "  cuuuum");

		if (i == 15)
			a(true, "  gay", "  gaaaaay", "  gaay", "  gaaay", "  gaaaaaaay");
		if (i == 16)
			a(true, "  vaj", "  vaaaaaj", "  vaaj", "  vaaaj", "  vaaaaaaaj",
					"  vajayjay");
		if (i == -1)
			a(true, "");
		// Leave two spaces for beginnings of sentences. Two spaces are done so
		// things like "Mass" does not get flagged as "Ass" simply because it
		// does not see the letter before.

		// clean
		if (cleanWords.isEmpty()) {
			a(false, " mass", "mass", "  mass", "the", "world", "some", "so",
					"such", "mynameisjeff", "woah", "text", "it", "does",
					"not", "very", "  very", "clean", "  clean", "come",
					"  come", "carrot", " klingon", "  captain", " kirk",
					" discord", "funky", "  funky", "matter", "what", "i",
					"somereallylongwordsto", "testonIdontknowifanyof",
					"thesewillactuallyvcomeupassear", "wordsbutitneedstobe",
					"thecasejustincase", "alongwordwithasuper",
					"hardasgadsgysdh", "idontknowhathappened",
					"therethekeyboatrsdasitasgtg", "dfgsgdfedashgiursdgafsgsd",
					"adfhduighewuwgfeAHSFDJKHFGVDSUJA",
					"ADSFNfvuiwhfiueghfgiusaediugv",
					"dsafeasuihfuierwahviudsa", "wequijqwehoiaodwqfdsbviuer",
					"tomgthaweisygwegasd", "safnweiofqwe9rfwsfa",
					"dsjufeifofoedlew;wpsolsmkf",
					"kdodirmnfiv,coewmjfdujfujdnfdjweowen", "type", "  hello",
					"  world", "  some", "  example", "  test", " messages",
					"  of", "of", "normal", "sneak", "jay", " jay", "  jay",
					"james", " james", "  james", "  jamse", "  sneak",
					"shifting", "  shifting", "  are", "  you", "  is",
					"jerky", "snowglobe", "canoue", "  vote", "  meat",
					" meet", "  idk", "  taxi", " booya", " bomb",
					"dictionairy", "coolio", " normal", "continue", "shihtzu",
					"zen", "zone", "  shihtzu", "zoo", "zoom", "picklerick",
					"  words", "  it", " can", " be", "anything",
					"greatgamegg", "anything", " anything", " heart", "  pvp",
					"  pillow", "  fluffy", "  gang", "  aything",
					"  factions", "  hypixel", "thehive", " that", " the",
					" reader", " would", " read", " cool", " heywantto",
					"coming", "following", "climbing", "flagging", "masking",
					"creating", " vroomvroom", "  vroom", "  vacuum",
					"  valcano", "  volcano", "  toast", "  cinnoman",
					"  cinamon", "  wat", "  hullo", "  hitman", "  cia",
					"  cyan", "  yellow", "  greem", "flinging", "shining",
					"  shining", "gliding", "swimming", "swarming", "shunning",
					"grappling", "sappling", "tradeforsome", "diamonds",
					"works", " works", "  works", "orks", "rks", "ks", "  s",
					"gold", "emerald", "do", " do", "  do", "  iron", "  mass",
					"mass", " mass", " geoff", "somerandom", "lengthofstring",
					"mother", " mother", "  mother", " father", "father",
					"son", "daughter", "forsomething", "op", "opis",
					"anythingelseyou", "wouldliketo", "add", "subtract",
					"wordassociation", "mispelword", "kik", "kek", "lol",
					"  lol", " lol", "goodjob", "Accordingtoall",
					"knownlawsofaviation", "thereisnowayabee", "shouldbeable",
					"toflyits", "muck", "  muck", "  suck", "suck", "guck",
					"  guck", "  yuck", "yuck", "anotherone", "ridesthebus",
					"wingsaretoo", "smalltogetits", "fatlittlebodyoff",
					"thegroundThe", "beeofcourse", "fliesanyway", "  derik",
					" deric", "imustcontest", " imustcontest",
					"  imustcontest", " domino", " jibberjaber", "  babel",
					" LDBS ", " TNAGA", "  FBUD ", " BRCUC", " CFHCB",
					"  PC C", " OBICB ", " SPHEA  ", " GOCCC ", " OFUUE",
					" FFAAA", " TTAP", " TTAMD", " TTTRA", " DPHEA  ",
					" BFCEC ", " LBHUA", " MBTCB  ", " LSFIA", " LSHPA ",
					" LSBNE", " FH G", " TTGEA ", " RPHAF ", " TTOFA ",
					" TBA A ", " TPBA  ", " THHH ", " HBIC ", "LFHHA ",
					" BMCU ", " NP A ", " B  G ", " B CUD ", " MRCU  ",
					" DPHAF  ", "IFIGB", " TPB F  ", "   AGA ", " DCBU",
					" TBIGA ", " TFBFB  ", " SPHGB", " LFUHA  ", " NBTC ",
					" BOCOA ", " PTAAA", " GPAG ", " TUTG", "TFHCB ", " TBAGA",
					" IPUUD", " CUHAF ", "LBUUB", " TTCTD", " GDIIB", " NDI C",
					" CBTCC ", " HDRIC ", "  PH A", " SCSUB ", " CHSGB ",
					" NCHGE ", " CBICC ", " CBH F  ", " PFLGA  ", " PFHB ",
					" IDMIC ", " PDITC ", " HDISC", " BBSGB ", "  FH  ",
					" NCDUB ", " NFCGA ", "  UH B ", "  UH   ", " PPBF  ",
					" M HGE ", " PCTU ", " ICUUE ", " LBIIC  ", " CIUU  ",
					" MR UA ", " GFFU ", " SFOCC ", " EPHFB", " SACOC",
					" DCSUC ", " BPMGE ", " GFFU ", " BPUG ", " PBIID",
					" CCBUD  ", " TT  A", " TTTRA", " HCUUB ", " M UUC",
					" SPUUD", " LPGGA", " LTHGA", " CH GB ", " TTTTD ",
					" MDHCC ", " DFUUD", " HDIGC ", " DSUUC ", " NFHD  ",
					" OPHE  ", " GPH A  ", " NPBFB", " HD IC", " IAIIC ",
					" SDIIC", " RSIIC ", " GFPU", " NFHA  ", " DPHF  ",
					"  FB A ", " NF CA ", " NFOOC ", " OFPCA ", " LBAAA",
					" GBBG  ", " OBCUA ", " FFCCA ", " LBUUC ", " S SUC",
					" PFHCA ", " NBHGC", "  battery", "science",
					"2tothe1tothe1tothe3", "fidgetspinners", "becausebeesdont",
					"can", "  can", "  who", "  bythepower", "vaginia", "one1",
					"  pen", "HELLOBARBIE", "letsgoparty", "party", "  party",
					"  marty", " sorry", "golden", "tennant", "tenant",
					"  tenant", "  tennent", " ofgrayskull", " zombie",
					" mummy", " mommy", "  daddy", "  kiddo", "  kid",
					"thatsright", "atright", "hatsright", "right", "sright",
					"meetatspawn", "somebody", "oncetoldme", "theworldis",
					"bacon", "  bacon", "biscut", "  biscut", " biscut",
					"totalbiscut", "abiscut", "country", "county", "community",
					"poppop", "nevergonna", "giveyouup", "nevergonna",
					"letyoudown", "nevergonnarunaround", "anddesertyou",
					"takeabow", "carewhathumansthink", "isimpossibleYellow",
					"blackYellowblack", "pool", "  pool", "pub", "  pub",
					" coolaid", "coolaid", "  cooliad", "  coke", "  pesi",
					"pepsi", "ispepsigood", "moomoo", "YellowblackYellow",
					"blackOoh", "blackandyellow", "letsshakeitupalittle",
					"ifweeverwanted", "toachieveinterestaller",
					"travelsomething", "somethingorother", "justsomeothertext",
					"moretextandstuff", "duck", "ducking", "fudge", "fudging",
					"  ducking", " ducking", " shipping", "shipping",
					"  shipping", " sharp", "cup", "similarities",
					" similarities", "  similarities", "  moola", "saga",
					"  saga", "  found", "found", " funded", " economy",
					" gtippling", "  crippling", " metrics", " updaters",
					"  updaters", "catface", "facebook", "  facebook",
					"minisoda", "mapple", "  mapple", "  staple", "stapple",
					"bones", "  bones", " mrhouse", "portal", "  portal2",
					"  jokingly", "  cakeisnota", "lie", "  lie",
					"youwannapvpin", "ouwannapvpinthe", "uwannapvpinthe",
					"wannapvpinthe", "annapvpinthesur", "nnapvpinthesurviv",
					"anpvpinthesurvival", "pvpinthesurvival", "vpinthesurival",
					"inthesurvival", "thesurvival", "survivalworld",
					"esurvivalworld", "vive", "vival", "  vival", "  vive",
					"diamondsword", "  diamondsword", " pickaxe", " goldaxe",
					" worldedit", "  vault", "vault", "fingerlakes", "fishy",
					"  fishy", "  fluffy", "  fury", " fungi", " mushroom",
					"  mushrooms", "  shrooms", "  vacuumes", "justdontstart",
					"crippling", "depression", "  depression", "joy",
					"democracy", "communism", "socialism", "pearls", "oil",
					"  oil", "  timber", "  tumbler", "  tumblr", "  reddit",
					"  cup", "chip", "  chip", "caesar", "thangs",
					"maybetheNNisoff", "weneedmoretesting", "morestuff", "guy",
					" guy", "  guy", "where", " where", "  where", "who",
					" who", "  who", "zebealo", "weeboasf", "forefsase",
					"dsfawdsddg", "dsafGASGDSAG", "stuifas", "testxrtwats",
					"period", "  wirds", "wereecsf", "zzzzdgfwasf", "mooooos",
					"thecowgoesmpoo", "dogswoof", "catsmoew", "cwomoo",
					"moretext", "swearsstuff", "hullo", "halo", "wynncraft",
					"mineplex", "spigot", "bukkit", "theshotbownetwork",
					"network", "hivemc", "thisisanexampletext", "sampletext",
					"gateeem", "lolwat", "  duck", " duck", "duck", "lick",
					" lick", "  lick", "luck", " luck", "  luck", "milk",
					" milk", "  milk", " juggler", "  farmer", "  ducking",
					"  dipping", " shuckling", "cooliokid", "Zombie_Striker",
					"Zombie", "Skeleton", "creeper", "cow", "chicken", "pig",
					"squid", "quack", "parrot", "silverfish", "fish",
					"redfish", "bluefish", "onefishtwofish", "enderman",
					"enderdragon", "dragon", "moretextthatisnotbad",
					"goodtext", "mrwsomethingcool", "happens", "fallout",
					"callofduty", "blackops", "liamneelson", "mattdamon",
					"hisnameisrobert", "paulson", "hisnameis", "robertpaulson",
					"fightclub", "incaseyouwerewondering", "plastic",
					"aluminium", "iron", "rock", "granite", "andersite",
					"obsidian", "queue", "joke", "batman", "superman",
					"aquaman", "wonderwoman", "grapefuit", "spiderman",
					"ironman", "hulk", "ironfist", "lukecage", "thor",
					"hhhhhug", "firstlook", "quick", "quit", "  quick",
					"thisisallgoodtext", "somethingthatwouldbe",
					"seenoneveryserver", "EVERYSERVER", "nonofitis",
					"justrabling", "ofadeveloper", "losingsleep",
					"becauseastupid", "neuralnetworkiis", "refusingtowork",
					"withasmallsamplesize", "WHYWONTYOU",
					"FILTEROUTSWEARWORDS", "thereisnotasingle",
					"swearwordinthisblock", "oftext", "noneofthese",
					"sometimes", "sometimesyou", "  youjusthave",
					"  sometimes", "letters", "tters", "ttters",
					"certainwords", "whycanyou", "canyoujustnot", "  justnot",
					"whyareyoudoing", "  thistome", "  whyareyoudoingthis",
					"flipping", "  flipping", " flunky", "flunky", "   flunky",
					"  flying", "flying", "  flyying", "false", "  false",
					"  full", "  filling", " flame", "  fling", " flung",
					" flop", "flop", "  flop", "little", "lame", "  lame",
					"assassin", " assassin", "  assassin", "  assasinscreed",
					"anassassin", "gas", "  gas", "deka", " daca",
					" waterboard", "lava", "  lava", "  villager", "witch",
					"potions", " potions", "keepthisinmind", "learnfromthis",
					"justdoit", "  justdoit", "makeyourdeams",
					"  makeyourdreams", "cometrue", "  cometrue", "true",
					"  true", "helpme", "  help", "howtoraisemaxhealth",
					"maxhealth", "thechancesaresmall", "butIhaveagood",
					"feelingthaththis", "isenough", "paulwalker", "nana",
					"  nana", "fdgfdshfdshssfdhdfsh", "fgahfsehtfhfshdfdsh",
					"fesgdhyhterssththrs", "yhtrrewwerqaf",
					"sdasfeqwAEWQFDWAS", "fgdhsdfzsaeaerfasd", "adsdsfgageraa",
					"sdwewerygytea", "fwsaddagrgered", "gsrfdgeyheaw",
					"awsawgrthesd", "uityfygffhgjg", "gfsddsfgrth",
					"ewrwtewsadata", "vzdvgdfssbx", "hnbdhfgnsdf",
					"vbfdsgbsde", "erdsadfasg", "ythsrdedfs", "uhyrfdjyds",
					"gzdfsadsgfhdsaes", "rtesajhuasdfzd", "gsaeasaWF",
					"asEAT4AES3AWS", "3452TGWAAEGRF", "532R3QGESEHR",
					"%WE4W3QHEREEAS", "454EUWRETQREAQQE", "5REYHERAAHER",
					"%$RESRTRT", "%^AEYFAFEAWRAS", "56RAEY", "543QTRwsht",
					"46uytjyd6rsb5", "e45t4awQRWET", "%AW4TTARWFEGU5",
					"45AYEWHRSTHRTD", "46T45UYKTUYD6Y45", "477IAERAREY",
					"54E5W3WQEWDFSAGD", "GFDESAHERS", "reghaseaasng",
					"mtuyfuyjgsd", "fdswrsgvw", "awefvcfdhnbtr",
					"bvtmutdstnfs", "brwarhgtjt", "uttrwwfdsabv", "verjurawG",
					"HWREJH4WEESDH", "wEfewoiqwFWEWrg", "IOQEFQEWPO",
					"FWQEHIUGVRHEUI", "VREUAGRWUQA", "FVUYEWQOQ",
					"CVNWJAUEWAS", "NQWViemwpo", "vbanujrugeiw",
					"cb wehjVCEWQ", "WEVDIOVHEOW", "WEGWOIEJ2QW", "ug4f2i3g",
					"42uigf43892q", "fb2y43fvu3f", "53uihgvaqawre", "vauweqg",
					"wergahuigewdoiugf", "sdafdswdfsdf", "ddddddd",
					"fggfghgggggg", "dsfgrweuhoger", "ghjauirehgoi", "grwe",
					"fhuiw4afu4w3h", "  gsfaduhrge", "egajhnurewvnqa",
					"vhrioeawovr", "vuireoagiue", "  grewreg", "g5iggwsfg",
					"gaufeiog", "gdvniaor", "gsakgjnewa", "gijr53eg43",
					"tgiejrsjhkteod", "efasuhfewai", "gfjhionawo", "anbuawech",
					"wefhiwraeg", "agnoius", "gavbiourwsavawf",
					"gerwauhirewfggrw", "rawvnuijewafn", "fgvawuihhva",
					"agwsIJBBGRWES", "ESVAUIFRVAESIU", "AWGVUIAWBUIOCWE",
					"VWUOINERWAA", "VRAIURREA", "NRTESAIUBREQa", "reawioewjh",
					"fwFUAWEGERA", "GVRAEIOUEFWIOGRQ", "WIOVBNWAHIOG4EW",
					"AWEBUEHGREWHAEW", "WAEUIOWEAhoiewgareg", "waeufesaiheaw",
					"awrewuaihgre", "awgrehera", "  f", "  fu", "  sh", "  cr",
					"  fa", " fe", "  va", "  ga", "  da", "  di", "  pi",
					"  mi", "  ni", "  bi", "  cu", " mass", "mass", "  mass",
					"   mass", "sass", " sass", "  sass", " gas", "  gas",
					" fast", " passive", "passive", "entitlement", "medicare",
					"toys", "  toy", "tims", " tims", "  tims", "its", " its",
					"  its", " mitten", "kitten", "  kitten", " pass", "pass",
					"  pass", "  fussy", "  doozie", " snoozie", "whole",
					"  whole", " whole", " would", "hold", " hold", "  hold",
					"gut", " gut", "  gut", "glut", " glut", "  glut", " fuss",
					" come", "come", "  come", " comment", " commit",
					"  guard", " fay", "say", " say", "  say", "yay", " yay",
					"  yay", " pay");

			a(false, "abcdefghijklmnopqrstuvwxyz".split(""));
			a(false,
					"  a.  b.  c.  d.  e.  f.  g.  h.  i.  j.  k.  l.  m.  n.  o.  p.  q.  r.  s.  t.  u.  v.  w.  v.  x.  y.  z"
							.split("."));
			a(false, "1234567890".split(""));
		}
	}
}
