package servicio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterListener;
import org.jivesoftware.smack.sasl.SASLMechanism;
import org.jivesoftware.smack.sasl.provided.SASLDigestMD5Mechanism;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.ping.PingFailedListener;
import org.jivesoftware.smackx.ping.PingManager;
import org.jivesoftware.smackx.ping.android.ServerPingWithAlarmManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import xyz.feliche.androidbam.Const;
import xyz.feliche.androidbam.MainActivity;

/**
 * Created by feliche on 21/06/16.
 */
public class XmppConnection implements ConnectionListener, ChatManagerListener, PingFailedListener, ChatMessageListener, RosterListener {

    private final Context mApplicationContext;
    private XMPPTCPConnection mConnection;
    private BroadcastReceiver mReceiver;

    private static final String LOGTAG = "XmppConnection:";

    // envía el estado de la conexión
    private void connectionStatus(ConnectionState status){
        Intent intent = new Intent(XmppService.UPDATE_CONNECTION);
        intent.setPackage(mApplicationContext.getPackageName());
        intent.putExtra(XmppService.CONNECTION, status.toString());
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN){
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        }
        mApplicationContext.sendBroadcast(intent);
    }

    @Override
    public void chatCreated(Chat chat, boolean createdLocally) {
        chat.addMessageListener(this);
    }

    @Override
    public void processMessage(Chat chat, Message message) {
        if (message.getType().equals(Message.Type.chat)
                || message.getType().equals(Message.Type.normal)) {
            if (message.getBody() != null) {
                Intent intent = new Intent(XmppService.NEW_MESSAGE);
                intent.setPackage(mApplicationContext.getPackageName());
                intent.putExtra(XmppService.BUNDLE_MESSAGE_BODY, message.getBody());
                intent.putExtra(XmppService.BUNDLE_FROM_XMPP, message.getFrom());
                mApplicationContext.sendBroadcast(intent);
            }
        }
    }

    public static enum ConnectionState{
        AUTHENTICATE,
        AUTH_ERROR,
        IO_ERROR,
        HOSTNAME_ERROR,
        CONNECTED,
        CLOSED_ERROR,
        RECONNECTING,
        RECONNECTED,
        RECONNECTED_ERROR,
        DISCONNECTED,
        START_SERVICE,
        PING_ERROR,
        STOP_SERVICE;
    }

    //ConnectionListener
    @Override
    public void connected(XMPPConnection connection) {
        XmppService.sConnectionState = ConnectionState.CONNECTED;
        connectionStatus(XmppService.sConnectionState);
    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {
        XmppService.sConnectionState = ConnectionState.AUTHENTICATE;
        connectionStatus(XmppService.sConnectionState);
    }

    @Override
    public void connectionClosed() {
        XmppService.sConnectionState = ConnectionState.DISCONNECTED;
        connectionStatus(XmppService.sConnectionState);
    }
    @Override
    public void connectionClosedOnError(Exception e) {
        XmppService.sConnectionState = ConnectionState.CLOSED_ERROR;
        connectionStatus(XmppService.sConnectionState);
    }
    @Override
    public void reconnectingIn(int seconds) {
        XmppService.sConnectionState = ConnectionState.RECONNECTING;
        connectionStatus(XmppService.sConnectionState);
    }
    @Override
    public void reconnectionSuccessful() {
        XmppService.sConnectionState = ConnectionState.RECONNECTED;
        connectionStatus(XmppService.sConnectionState);
    }
    @Override
    public void reconnectionFailed(Exception e) {
        XmppService.sConnectionState = ConnectionState.RECONNECTED_ERROR;
        connectionStatus(XmppService.sConnectionState);
    }

    @Override
    public void pingFailed() {
        Log.i("XMPPConnection: ", "Fallo el ping");
        XmppService.sConnectionState = ConnectionState.PING_ERROR;
        connectionStatus(XmppService.sConnectionState);

        Intent intent = new Intent(mApplicationContext, XmppService.class);
        mApplicationContext.stopService(intent);
        mApplicationContext.startService(intent);
    }

    public XmppConnection(Context mContext){
        mApplicationContext = mContext.getApplicationContext();
    }

    // Desconectar
    public void disconnect(){
        if(mConnection != null){
            mConnection.disconnect();
        }
        mConnection = null;
        if(mReceiver != null){
            mApplicationContext.unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }


    public void onConnectionError(ConnectionState error){
        connectionStatus(error);
    }

    private void setUpSendMessageReceiver(){
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if(action.equals(XmppService.SEND_MESSAGE)){
                    sendMessage(intent.getStringExtra(XmppService.BUNDLE_MESSAGE_BODY),
                            intent.getStringExtra(XmppService.BUNDLE_TO));
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(XmppService.SEND_MESSAGE);
        mApplicationContext.registerReceiver(mReceiver, filter);
    }

    // Envía un mensaje
    private void sendMessage(String mensaje, String toJabberId){
        Log.i("XmppConnection", "Enviando mensaje");
        Chat chat = ChatManager.getInstanceFor(mConnection).createChat(toJabberId);
        try {
            chat.sendMessage(mensaje);
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        }
    }

    // Conectar
    public void connect() throws IOException, XMPPException, SmackException {
        XMPPTCPConnectionConfiguration.Builder builder
                = XMPPTCPConnectionConfiguration.builder();

        builder.setSecurityMode(ConnectionConfiguration.SecurityMode.required);
        SASLMechanism mechanism = new SASLDigestMD5Mechanism();
        SASLAuthentication.registerSASLMechanism(mechanism);
        SASLAuthentication.blacklistSASLMechanism("SCRAM-SHA-1");
        SASLAuthentication.blacklistSASLMechanism("DIGEST-MD5");

        // configuración de la conexión XMPP
        // builder.setDebuggerEnabled(true);
        builder.setHost(Const.SERVER_NAME);
        builder.setServiceName(Const.SERVER_NAME);
        builder.setResource(Const.APP_NAME);
        builder.setSendPresence(true);
        builder.setPort(5222);

        // crea la conexión
        mConnection = new XMPPTCPConnection(builder.build());

        // set reconnection policy
        // set reconnection default policy
        ReconnectionManager connMgr = ReconnectionManager.getInstanceFor(mConnection);
        connMgr.enableAutomaticReconnection();
        connMgr.setEnabledPerDefault(true);
        connMgr.setDefaultReconnectionPolicy(ReconnectionManager.
                ReconnectionPolicy.FIXED_DELAY);

        // Configura el listener
        mConnection.addConnectionListener(this);
        // se conecta al servidor
        mConnection.connect();

        // Envía un Ping cada 60 segundos
        PingManager pingManager = PingManager.getInstanceFor(mConnection);
        pingManager.setDefaultPingInterval(600);
        pingManager.registerPingFailedListener(this);

        ServerPingWithAlarmManager.getInstanceFor(mConnection);
        ServerPingWithAlarmManager.onCreate(mApplicationContext);

        setUpSendMessageReceiver();

        Presence presence = new Presence(Presence.Type.available);
        presence.setStatus("Trabajando");
        mConnection.sendStanza(presence);

        ChatManager.getInstanceFor(mConnection).addChatListener(this);

        Roster roster = Roster.getInstanceFor(mConnection);
        roster.setRosterLoadedAtLogin(true);
        roster.setSubscriptionMode(Roster.SubscriptionMode.accept_all);
        roster.addRosterListener(this);

        // envía la autenticación
        mConnection.login(Const.SERVER_SMS_ACCOUNT, Const.SERVER_SMS_PASS);
    }

    private void rebuildRoster(){
        ArrayList<String> listRoster = new ArrayList<String>();
        Roster roster = Roster.getInstanceFor(mConnection);

        if(!roster.isLoaded()) {
            try {
                roster.reloadAndWait();
            } catch (SmackException.NotLoggedInException e) {
                e.printStackTrace();
            } catch (SmackException.NotConnectedException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Collection<RosterEntry> entries = roster.getEntries();
        String status;
        Presence presence;
        for(RosterEntry entry: entries){
            presence = roster.getPresence(entry.getUser());
            if(presence.isAvailable()){
                status = "OnLine";
            }else{
                status = "OffLine";
            }
            listRoster.add(entry.getUser() + ": " + status);
        }
        Intent intent = new Intent(XmppService.BUNDLE_ROSTER);
        intent.setPackage(mApplicationContext.getPackageName());
        intent.putExtra(XmppService.LIST_ROSTER, listRoster);
        mApplicationContext.sendBroadcast(intent);
    }

    @Override
    public void entriesAdded(Collection<String> addresses) {
        Log.d(LOGTAG, "Se agrego un usuario");
        rebuildRoster();
    }
    @Override
    public void entriesUpdated(Collection<String> addresses) {
        Log.d(LOGTAG, "Se actualizo la lista");
        rebuildRoster();
    }
    @Override
    public void entriesDeleted(Collection<String> addresses) {
        Log.d(LOGTAG, "Se elimino un usuario");
        rebuildRoster();
    }
    @Override
    public void presenceChanged(Presence presence) {
        rebuildRoster();
        Log.d(LOGTAG, "cambio el estado de un usuario");
    }
}