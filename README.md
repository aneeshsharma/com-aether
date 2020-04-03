# Com-Aether

Com-Aether is an end to end messaging app for secure messaging without any tracking. This allows you to communicate with assurance that nobody can peek into your conversations except you and the receiver. It is achieved by only storing the massages locally. The server only acts as a relay to connect to people and communicate back and forth. The communication between 2 peers is completely encrypted and so, even the server can't see any messages.

### First release now available

## Sections in this guide
1. [Setting up a server](#server)
2. [Using the client](#client)
3. [Commands list](#commands)

## How to use it?
To start using com-aether, you first need to setup a chat server as there is no default chat server yet (the default chat server that the app uses is 'localhost' and is for testing purpose only).

> Java 11 has been used to build the client as well as the server. Make sure you have the same version when running the applications

### <a name="server"></a>Setting up a server
Hosting the server is easy. First of all make sure you have MySQL installed. To install MySQL-
1. Debian based systems : ```sudo apt install mysql-server```

Once that is done make sure you create a new user for the server app. For now the server app uses the default credentials on MySQL as -
```
Username - com-aether
Password - letschat
```
To setup a new user and a database for the server, first run MySQL as root (`sudo mysql`). Then -
```
mysql> CREATE USER 'com-aether'@'localhost' IDENTIFIED WITH mysql_native_password by 'letschat';
```
Note that `mysql_native_password` is important here as the JDBC driver in the server app will be unable to authenticate using a normal password.
Now create a database named `com_aether_db` and grant the relevant permissions -
```
mysql> CREATE DATABASE com_aether_db;
mysql> GRANT ALL PRIVILEGES ON com_aether_db.* TO 'com-aether'@'localhost';
```

The SQL server is now ready to go!

Now to run the server app, navigate to the directory where you have downloaded the `com-aether-server.jar` and execute it using -
```
$ java -jar com-aether-server.jar
```
This should now run the server on Port 7200. The server app will manage the rest (creating required table etc.) on the first connection.

The server is not very configurable as of now. I plan to add a configuration file in the future inorder to make the server setup even simpler.

### <a name="client"></a>Using the client
Once the server is up and running. We can start using the client to chat!

The client is a simple command line application. To run the client, download the latest client app, navigate to the directory and then -
```
$ java -jar com-aether-client.jar
```
If its your first run, the app starts by asking the server address. Type in the URL of the server that you have setup. `localhost` if you have setup the server on the local machine. 

After that, it presents a prompt as `(DEBUG) User:`. This is a debug feature added to create multiple user accounts on a single device. Enter a username that you mean to be identified by on your local machine.

The rest of the registration is fairly straightforward.

After you have logged in, you are presented with a simple prompt as `>>> <command>`. You can now interact with the app using commands.

### <a name="commands"></a>Commands
Different commands have been created in Com-Aether to efficiently interact with the app using your keyboard. Its faster than GUI.

The different commands are -

1. `>>> /connect <username>` - connects you to a user with the username on the chat server. Essential to start any conversation
2. `>>> /send <username>:<message>` - To send a messgae to a user with username as `<username>`
3. `>>> /update` - To update the client with new messages and connection requests
4. `>>> /get_messages <username> [optional <number of messages>]` - To get all messages from a chat with username as `<username>`. Optionaly you can also provide number of messages to fetch. By defulat fetches last 10 messages.

These commands are bare minimum of what a chat app should be capable of doing. This interface is obviously not very user friendly. But this is a bare bones built for testing the underlying mechanism. A proper GUI will be coming soon. But don't worry. For the keyboard lovers, all commands will be incorporated in the app, so that you never have to reach that mouse again.
