package com.dechiridas.bitauth.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.dechiridas.bitauth.BitAuth;

public class ConnectionManager {
	private BitAuth plugin;
	
	// Database info
	private String user = ""; // db username
	private String pass = ""; // db password
	private String url = ""; // db url
	
	// Database connection object
	Connection conn = null;
	
	public ConnectionManager(BitAuth instance) {
		this.plugin = instance;
		
		// Database configuration
		user = plugin.config.getString("settings.database.username");
		pass = plugin.config.getString("settings.database.password");
		url = "jdbc:mysql://" + plugin.config.getString("settings.database.host") +
			"/" + plugin.config.getString("settings.database.database");
	}
	
	// Opens a new connection to the database
	public void openConnection() {
		try {
			plugin.log.println("BitAuth: Creating new connection to database..."); // debug?
			conn = DriverManager.getConnection(url, user, pass);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	// Returns the connection that BitAuth is using if it's still open,
	// otherwise creates a new connection and returns that
	public Connection getConnection() {
		try {
			// Default MySQL timeout is 8 hours, thus, if the DB connection goes
			// completely unused for 8 hours, logging in (etc) will take 4 extra seconds
			if (!conn.isValid(4)) {
				if (!conn.isClosed())
					closeConnection();
				plugin.log.println("BitAuth: Oh no! Connection was invalid or closed for whatever reason."); // debug?
				openConnection();
			} else {
				plugin.log.println("BitAuth: BitAuth re-used an old connection!"); // debug
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return conn;
	}
	
	// Closes the connection to the database
	public void closeConnection() {
		try {
			plugin.log.println("BitAuth: Closing old connection to the database..."); // debug?
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
