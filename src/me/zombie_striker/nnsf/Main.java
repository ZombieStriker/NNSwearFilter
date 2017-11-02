package me.zombie_striker.nnsf;

import java.io.File;
import java.io.IOException;

import me.zombie_striker.neuralnetwork.NNBaseEntity;
import me.zombie_striker.neuralnetwork.NeuralNetwork;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.*;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class Main extends JavaPlugin implements Listener {

	private NeuralNetwork[] swearbots = new NeuralNetwork[SwearFilterBot.swearlist.length];
	private boolean training = false;
	private int internalID = 9;
	
	private File messages = new File(getDataFolder(),"messages.yml");
	private FileConfiguration config=null;
	
	public static String NO_SWEAR = "&cDo not swear. Found similarities of \"%swear%\" in \"%word%\"";

	public NNBaseEntity getEntity(int k) {
		if (getConfig().contains("NNSaves." + k)) {
			return (NNBaseEntity) getConfig().get("NNSaves." + k);
		}
		return new SwearFilterBot(true, k);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onChat(AsyncPlayerChatEvent e) {
		if (training)
			return;

		// If it is not training, and if the demo is set to swearbot, then do
		// the following:

		StringBuilder chat = new StringBuilder();
		chat.append("  ");
		for (char c : e.getMessage().toUpperCase().toCharArray()) {
			if (c != ' ' && c != '?' && c != '.' && c != ',' && c != '!')
				chat.append(c);
		}
		// Add two spaces before the chat message, remove all spaces and
		// punctuation marks so 's?h.i t' is treated as 'shit'

		for (int i = 0; i < chat.toString().length()-2; i++) {
			String testingString = chat.toString().substring(i);
			// We are using a scanner approach. This will offset the string by 1
			// char until it is at the last letter.
			for (NeuralNetwork k : swearbots) {
				((SwearFilterBot) k.getCurrentNeuralNetwork()).word
						.changeWord(testingString);
				// Loop through all the swear types. Testt it for each NN.

				boolean detectsSwearWord = ((SwearFilterBot) k
						.getCurrentNeuralNetwork()).tickAndThink()[0];
				if (detectsSwearWord) {
					// The bot detects a similarity to a swear word. May be a
					// swear.
					
					String w = ((SwearFilterBot) k
							.getCurrentNeuralNetwork()).filterType;
					w=w.charAt(0)+"*"+w.substring(2);
					e.setCancelled(true);
					e.getPlayer()
							.sendMessage(
									ChatColor.DARK_RED
											+ "[SwearBot]"
											+ChatColor.translateAlternateColorCodes('&',NO_SWEAR.replaceAll("%swear%", w).replaceAll("%word%",testingString)));
					return;
				}
			}
		}
	}

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		ConfigurationSerialization.registerClass(SwearFilterBot.class);
		final JavaPlugin p = this;
		new Updater(this,280707);
		new Metrics(p);
		
		boolean a = false;
		if(!messages.exists()){
			try {
				messages.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			a=true;
		}
		config = YamlConfiguration.loadConfiguration(messages);
		if(a){
			config.set("WarningMessage", NO_SWEAR);
			try {
				config.save(messages);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		NO_SWEAR = config.getString("WarningMessage");
		
		
		
		new BukkitRunnable() {
			@Override
			public void run() {
				try {
					if (Bukkit.getPluginManager().getPlugin("NeuralNetworkAPI") == null)
						new DependencyDownloader(p, 280241);
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				if (!new File(getDataFolder(), "config.yml").exists()
						|| !getConfig().contains("NNAI_ID")
						|| getConfig().getInt("NNAI_ID") < internalID) {
					saveDefaultConfig();
				}
				for (int i = 0; i < SwearFilterBot.swearlist.length; i++) {
					NNBaseEntity b = getEntity(i);
					if (swearbots[i] == null)
						swearbots[i] = new NeuralNetwork(p);
					swearbots[i].setCurrentNeuralNetwork(b);
					// swearbots[id].setBroadcasting(false);
				}
			}
		}.runTaskLater(this, 0);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command,
			String label, String[] args) {
		if (args.length == 0) {
			sender.sendMessage("args: learn, stop, save, print");
			return true;
		}
		if (args[0].equalsIgnoreCase("learn")) {
			training = true;
			for (NeuralNetwork nn : swearbots) {
				nn.startLearningAsynchronously();
			}
			sender.sendMessage("Started training. Expect lag.");
		}
		if (args[0].equalsIgnoreCase("stop")) {
			training = false;
			for (NeuralNetwork nn : swearbots) {
				if(nn!=null)
				nn.stopLearning();
			}
			sender.sendMessage("Started stopped training.");
		}
		if (args[0].equalsIgnoreCase("list")) {
			sender.sendMessage("Training :"+training);
			for (int i = 0;i < swearbots.length;i++) {
				sender.sendMessage(i+": "+((SwearFilterBot)swearbots[i].getCurrentNeuralNetwork()).filterType);
			}			
		}
		if (args[0].equalsIgnoreCase("save")) {
			for (NeuralNetwork nn : swearbots) {
				getConfig()
						.set("NNSaves."
								+ ((SwearFilterBot) nn
										.getCurrentNeuralNetwork()).filterid,
								nn.getCurrentNeuralNetwork());
			}
			int saveid = 0;
			if (getConfig().contains("NNAI_ID")) {
				saveid = getConfig().getInt("NNAI_ID");
			}
			saveid++;
			getConfig().set("NNAI_ID", saveid);
			saveConfig();
		}
		return true;
	}
}
