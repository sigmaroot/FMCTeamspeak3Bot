package de.sigmaroot.fmc;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.TS3Config;
import com.github.theholywaffle.teamspeak3.TS3Query;
import com.github.theholywaffle.teamspeak3.api.TextMessageTargetMode;
import com.github.theholywaffle.teamspeak3.api.event.*;
import com.github.theholywaffle.teamspeak3.api.event.TextMessageEvent;
import com.github.theholywaffle.teamspeak3.api.wrapper.ClientInfo;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;

import org.ini4j.Ini;

public class Main {

    private static Ini iniFile;
    private static boolean enable_debug = true;
    private static String mysql_host = "";
    private static String mysql_port = "";
    private static String mysql_user = "";
    private static String mysql_password = "";
    private static String mysql_database = "";
    private static String mysql_table = "";
    private static String ts3_ip = "";
    private static String ts3_login = "";
    private static String ts3_password = "";
    private static String ts3_nickname = "";
    private static int ts3_membergroup = 0;
    private static boolean ts3_addguestgroup = false;
    private static int ts3_guestgroup = 0;
    private static int ts3_termlength = 0;
    private static ArrayList<String> termsofuse = new ArrayList<String>();

    private static void writeDB(int id, String uid, String nick, String ip) {
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://" + mysql_host + ":" + mysql_port + "/"
                + mysql_database + "?useSSL=false&serverTimezone=UTC", mysql_user, mysql_password);
                Statement stmt = conn.createStatement();) {
            String strInsert = "INSERT INTO " + mysql_table + " VALUES (NULL, NULL, " + String.valueOf(id) + ", '" + uid
                    + "', '" + nick + "', '" + ip + "')";
            stmt.executeUpdate(strInsert);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // INI settings
        try {
            iniFile = new Ini(new File("settings.ini"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            enable_debug = Boolean.valueOf(iniFile.get("general", "debug"));
            mysql_host = String.valueOf(iniFile.get("mysql", "host"));
            mysql_port = String.valueOf(iniFile.get("mysql", "port"));
            mysql_user = String.valueOf(iniFile.get("mysql", "user"));
            mysql_password = String.valueOf(iniFile.get("mysql", "password"));
            mysql_database = String.valueOf(iniFile.get("mysql", "database"));
            mysql_table = String.valueOf(iniFile.get("mysql", "table"));
            ts3_ip = String.valueOf(iniFile.get("teamspeak", "ip"));
            ts3_login = String.valueOf(iniFile.get("teamspeak", "login"));
            ts3_password = String.valueOf(iniFile.get("teamspeak", "password"));
            ts3_nickname = String.valueOf(iniFile.get("teamspeak", "nickname"));
            ts3_membergroup = Integer.valueOf(iniFile.get("teamspeak", "membergroup"));
            ts3_addguestgroup = Boolean.valueOf(iniFile.get("teamspeak", "addguestgroup"));
            ts3_guestgroup = Integer.valueOf(iniFile.get("teamspeak", "guestgroup"));
            ts3_termlength = Integer.valueOf(iniFile.get("terms", "length"));
            for (int i = 1; i <= ts3_termlength; i++) {
                termsofuse.add(String.valueOf(iniFile.get("terms", "term" + String.valueOf(i))));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        final TS3Config config = new TS3Config();
        config.setHost(ts3_ip);
        config.setEnableCommunicationsLogging(enable_debug);

        final TS3Query query = new TS3Query(config);
        query.connect();

        final TS3Api api = query.getApi();
        api.login(ts3_login, ts3_password);
        api.selectVirtualServerById(1);
        api.setNickname(ts3_nickname);
        api.sendServerMessage(ts3_nickname + " ist online!");
        api.registerAllEvents();
        api.addTS3Listeners(new TS3Listener() {

            @Override
            public void onTextMessage(TextMessageEvent e) {
                int invokerid = e.getInvokerId();
                int botid = api.whoAmI().getId();
                if (invokerid == botid) {
                    return;
                }
                if (e.getTargetMode() == TextMessageTargetMode.CHANNEL) {
                    if (e.getMessage().equalsIgnoreCase("pingbot")) {
                        api.sendPrivateMessage(invokerid, "Ich bin anwesend!");
                    }
                }
                if (e.getTargetMode() != TextMessageTargetMode.CLIENT) {
                    return;
                }
                if (e.getTargetClientId() != botid) {
                    return;
                }
                ClientInfo cinfo = api.getClientByUId(e.getInvokerUniqueId());
                int cdbid = cinfo.getDatabaseId();
                int cservergroups[] = cinfo.getServerGroups();
                for (int i = 0; i < cservergroups.length; i++) {
                    if (cservergroups[i] == ts3_membergroup) {
                        api.sendPrivateMessage(invokerid, "Du hast die Nutzungsbedingungen bereits angenommen!");
                        return;
                    }
                }
                if (e.getMessage().equalsIgnoreCase("AKZEPTIEREN")) {
                    api.sendPrivateMessage(invokerid, "Vielen Dank und viel Spaß auf unserem TS3!");
                    api.addClientToServerGroup(ts3_membergroup, cdbid);
                    if (ts3_addguestgroup) {
                        api.addClientToServerGroup(ts3_guestgroup, cdbid);
                    }
                    writeDB(cinfo.getDatabaseId(), cinfo.getUniqueIdentifier(), cinfo.getNickname(), cinfo.getIp());
                } else {
                    api.sendPrivateMessage(invokerid, "Du musst die Nutzungsbedingungen mit \"AKZEPTIEREN\" annehmen!");
                }
            }

            @Override
            public void onServerEdit(ServerEditedEvent e) {
                // ...
            }

            @Override
            public void onClientMoved(ClientMovedEvent e) {
                // ...
            }

            @Override
            public void onClientLeave(ClientLeaveEvent e) {
                // ...
            }

            @Override
            public void onClientJoin(ClientJoinEvent e) {
                String cuid = e.getUniqueClientIdentifier();
                int cid = e.getClientId();
                try {
                    ClientInfo cinfo = api.getClientByUId(cuid);
                    int cservergroups[] = cinfo.getServerGroups();
                    for (int i = 0; i < cservergroups.length; i++) {
                        if (cservergroups[i] == ts3_membergroup) {
                            return;
                        }
                    }
                    api.pokeClient(cid,
                            "Hallo! Ich habe dir eine Nachricht wegen unseren Nutzungsbedingungen geschickt!");
                    for (int i = 0; i < termsofuse.size(); i++) {
                        api.sendPrivateMessage(cid, termsofuse.get(i));
                    }
                } catch (Exception exception) {
                    // TODO: handle exception
                }
            }

            @Override
            public void onChannelEdit(ChannelEditedEvent e) {
                // ...
            }

            @Override
            public void onChannelDescriptionChanged(ChannelDescriptionEditedEvent e) {
                // ...
            }

            @Override
            public void onChannelCreate(ChannelCreateEvent e) {
                // ...
            }

            @Override
            public void onChannelDeleted(ChannelDeletedEvent e) {
                // ...
            }

            @Override
            public void onChannelMoved(ChannelMovedEvent e) {
                // ...
            }

            @Override
            public void onChannelPasswordChanged(ChannelPasswordChangedEvent e) {
                // ...
            }

            @Override
            public void onPrivilegeKeyUsed(PrivilegeKeyUsedEvent e) {
                // ...
            }
        });
    }

}
