package bitlegend.bitauth;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.file.YamlConfiguration;

public class Config {
	private BitAuth instance;
	public String directory = "plugins" + File.separator + BitAuth.class.getSimpleName();
	File file = new File(directory + File.separator + "config.yml");
	
	public Config(BitAuth instance) {
		this.instance = instance;
	}
	
	public void checkConfig() {
		new File(directory).mkdir();
		if (!file.exists()) {
			try {
				file.createNewFile();
				addDefaults();
			} catch (Exception e) {
				System.out.println(instance.getDescription().getName()
						+ ": Unable to create config file.");
			}
		} else {
			loadKeys();
		}
	}
	
	public YamlConfiguration load() {
		try {
			YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
			return config;
		} catch (Exception e) {
			System.out.println(instance.getDescription().getName() +
					": Unable to load config file.");
		}
		return null;
	}
	
	private void addDefaults() {
		System.out.println("Generating Config file...");
		write(instance.enableOnStart, true);
		write("Use_Whitelist", false);
		write("DB_User", "username");
		write("DB_Name", "database");
		write("DB_Pass", "password");
		write("DB_Host", "hostipaddress");
		write("DB_Table_login", "logintable");
		write("DB_Table_whitelist", "whitelisttable");
		write("Debug_Mode", false); //Set this to false or even completely remove this line
	}
	
	private void loadKeys() {
		System.out.println("Loading Config File...");
		instance.enabled = readBoolean(instance.enableOnStart);
	}
	
	public void write(String root, Object x) {
		YamlConfiguration config = load();
		config.set(root, x);
		try {
			config.save(file);
		} catch (IOException e) {
			System.out.println("There was an error saving configuration to file " + file.getName());
		}
	}
	
	public Boolean readBoolean(String root) {
		YamlConfiguration config = load();
		return config.getBoolean(root, true);
	}
	
	public Double readDouble(String root) {
		YamlConfiguration config = load();
		return config.getDouble(root, 0);
	}
	
	public String readString(String root) {
		YamlConfiguration config = load();
		return config.getString(root);
	}
}
