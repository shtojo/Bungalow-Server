# Bungalow-Server
A home security application for GE/Networx/Caddx and similar systems - Server side application

Bungalow home security application
Author: Shawn Johnston

Note: There are two applicable GitHub repositories:
Bungalow-Android: Repository containing the Android phone application.
Bungalow-Server: Repository containing the Server-side application that connects to the security panel and runs on Linux (Raspberry Pi).

What is it?
This software consists of an Android phone application and a server application. The server application connects to a common security system panel which was sold under various names including Caddx, GE Security, Networx and possibly others. The model I am using is NX-8. The Android application runs on Android phones/devices and allows you to connect to your security system from anywhere in the world (by communicating from the phone application to the server application). It allows me to use my security system without paying any fees for a monitoring service. For any events, like an alarm event, it can send an email to my phone describing the event. I can connect at any time from my phone and see the status of all zones, arm/disarm, see a log of past events, etc. I can also set up rules to email me or speak (through a speaker in my home), etc based on certain events. The server application reviews the status of zones and reports any open zones every half hour. For example, if I leave the garage door open at night, it tells me for example, "Good evening, it 10pm and I would just like to remind you that your garage double-door is open". I can also have it speak and control the volume from my phone (if I need to get my child's attention when I'm away and they don't respond to their phone). When the application is started it builds a table of zone info and sets the correct system date and time for the security system and begins monitoring the system status.

History
When I moved into my home it had a security system and I wanted more features and did not want to pay for remote monitoring. I saw that the panel had a serial port and did a lot of reverse engineering of the protocol to determine how to communicate to the panel. At the time there wasn’t a lot of info on the web, however that was a dozen years ago, there is probably a lot more info available now. Through my work I was able to develop this software. 
I wrote this software around 2012 in C# running on a small Windows PC connected to my security system. After a few years I wanted to move to Linux for various reasons including being able to run on a tiny Raspberry Pi Linux computer and running a free operating system. So, I rewrote the software in Java so it would run on Linux on a Raspberry Pi. If you are not familiar, Raspberry Pi is a tiny Linux computer that is very small, low power and can be purchased for under $50.

The server application runs on Raspberry Pi which connects to the security panel's serial port. When the server application is first started, it queries the panel and builds what I call a 'virtual panel' in software. The security panel only sends out state-change events (door opens/closes, motion sensed, etc). So once this virtual panel is built holding all current states, it is updated every time something changes state. The result is the software always knows the status of all zones. The server application performs other functions such as sending emails for events, speaking, etc.

Communication to and from the phone application to the server is encrypted (AES256 or 128 with salt/hash and also uses a handshake sequence and will drop any connection that doesn't follow the required protocol. 

Speaking is done using pre-recorded short sound files that it concatenates together to make sentences. Originally, in my older C# application it used text-to-speech which allowed me to have it speak anything I wanted when I connected remotely from my phone. This was fun but it relied on a Windows text-to-speech function that was not available in Linux. Therefore, I changed the software when I rewrote it for Linux to use sound clips instead. So, it now uses many small audio clips that contain only a word, number or short phrase. The software builds sentences from these short audio clips, and it plays back smoothly. This works well in Linux and requires much less cpu resources also. The sound clips are documented in the Speaker.java file. 

After many years of using this software in my own home, I am now open-sourcing it in case anyone else can benefit from it and would like to use it or build on it.

Until I am able to document further, please review the source code for more details and capabilities.

I hope you find it useful. Thank you.
