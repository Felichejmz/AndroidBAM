package xyz.feliche.androidbam;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.StrictMode;
import android.os.Vibrator;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import servicio.XmppConnection;
import servicio.XmppService;

public class MainActivity extends AppCompatActivity {
    private EditText etHistory;
    private EditText etMsg;
    private Button btnConnect;
    //private int SIZE_CODE = 6;
    //private int interval = 10000;
    //private Handler handler;
    //ConcurrentHashMap<String, String> cola = new ConcurrentHashMap<String, String>();

    WakefulBroadcastReceiver wBReceiver;
    boolean registeredBR = false;

    String accountXmpp, passXmpp;

    private static final String MAINTAG = "Actividad PRINCIPAL:";
    boolean userConnection = false;

    // intents de SMS
    String SENT = "SMS_SENT";
    String DELIVERED = "SMS_DELIVERED";
    PendingIntent sentPI;
    PendingIntent deliveredPI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // mensaje adicional al usuario
        etMsg = (EditText)findViewById(R.id.etMsg);
        etHistory = (EditText)findViewById(R.id.etHistory);
        btnConnect = (Button)findViewById(R.id.btnConnect);

        accountXmpp = Const.SERVER_SMS_ACCOUNT;
        passXmpp = Const.SERVER_SMS_PASS;

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        //handler = new Handler();
        //startRepeatingTask();

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode())
                {
                    case Activity.RESULT_OK:
                        Toast.makeText(getBaseContext(), "SMS enviado", Toast.LENGTH_SHORT).show();
                        etHistory.append("\nSMS enviado");
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Toast.makeText(getBaseContext(), "Falla generica SMS", Toast.LENGTH_SHORT).show();
                        etHistory.append("\nSMS Falla generica");
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Toast.makeText(getBaseContext(), "Sin servicio SMS", Toast.LENGTH_SHORT).show();
                        etHistory.append("\nSin servicio SMS");
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Toast.makeText(getBaseContext(), "Sin PDU SMS", Toast.LENGTH_SHORT).show();
                        etHistory.append("\nSMS sin PDU");
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Toast.makeText(getBaseContext(), "Radio Off", Toast.LENGTH_SHORT).show();
                        etHistory.append("\nSMS radio off");
                        break;
                }
            }
        }, new IntentFilter(SENT));

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()){
                    case Activity.RESULT_OK:
                        Toast.makeText(getBaseContext(),"SMS entregado", Toast.LENGTH_SHORT).show();
                        etHistory.append("\nSMS entregado");
                        break;
                    case Activity.RESULT_CANCELED:
                        Toast.makeText(getBaseContext(),"SMS no entregado", Toast.LENGTH_SHORT).show();
                        etHistory.append("\nSMS NO entregado");
                        break;
                }
            }
        }, new IntentFilter(DELIVERED));

        sentPI = PendingIntent.getBroadcast(this,0,new Intent(SENT),0);
        deliveredPI = PendingIntent.getBroadcast(this,0,new Intent(DELIVERED),0);

        if(XmppService.getState().equals(XmppConnection.ConnectionState.DISCONNECTED)){
            etHistory.setText("Desconectado");
            drawBtnRed("Desconectado");
        }else if(XmppService.getState().equals(XmppConnection.ConnectionState.CONNECTED)){
            etHistory.setText("Conectado");
            drawBtnGreen("Conectado");
        }
    }

    // almacena el estado de la aplicación
    @Override
    protected void onSaveInstanceState(Bundle saveStatus){
        super.onSaveInstanceState(saveStatus);
        saveStatus.putBoolean("broadcastReceiver",registeredBR);
        saveStatus.putBoolean("connected", userConnection);
    }

    // retorna el estado de la aplicación
    @Override
    protected void onRestoreInstanceState(Bundle restoreStatus){
        super.onRestoreInstanceState(restoreStatus);
        registeredBR = restoreStatus.getBoolean("broadcastReceiver");
        userConnection = restoreStatus.getBoolean("connected");
    }

    @Override
    protected void onResume(){
    super.onResume();
        wBReceiver = new WakefulBroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(MAINTAG, "action = " + action);
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                switch (action) {
                    case XmppService.NEW_MESSAGE:
                        String message = intent.getStringExtra(XmppService.BUNDLE_MESSAGE_BODY);
                        etHistory.append("\n" + "Nuevo mensaje Xmpp:" + message);
                        if (testXmppMsg(message) == false) break;
                        String numero = message.split("@")[0];
                        String codigo = message.split("@")[1];
                        if (testCellMsg(numero, codigo) == false) break;
                        //Agrega solo si no esta en la lista el numero
                        // y no se puede quitar el limite de SMS's
                        //if(!cola.containsKey(numero)) {
                        //    cola.put(numero, codigo);
                        //    vibrator.vibrate(500);
                        //}
                        vibrator.vibrate(500);
                        sendSMS(numero, codigo);
                        break;
                    // Estado de la conexión XMPP
                    case XmppService.UPDATE_CONNECTION:
                        String status = intent.getStringExtra(XmppService.CONNECTION);
                        etHistory.append("\n" + "XMPP_UPDATE_CONNECTION:" + status);
                        if (status.equals("AUTHENTICATE")) {
                            drawBtnGreen("En linea");
                            connectXmpp();
                            etHistory.append("\n" + "en linea");
                        } else if(status.equals("CONNECTED")){
                            drawBtnGreen("Conectado");
                        } else if (status.equals("DISCONNECTED")) {
                            drawBtnRed("Desconectado");
                        } else if (status.equals("CLOSED_ERROR")) {
                            drawBtnRed("Error");
                        } else if(status.equals("HOSTNAME_ERROR")){
                            drawBtnRed("Error hostname");
                        }
                        break;
                    // Cambio de la conexión a Internet
                    case XmppService.CHANGE_CONNECTIVITY:
                        etHistory.append("\n" + "XMPP_CHANGE_CONNECTIVITY:" + " userConection:" + userConnection);
                        etHistory.append("\nEstado conexion XMPP:" + XmppService.getState());
//                        if(userConnection == false) break;
//                        if(XmppService.getState().equals(XmppConnection.ConnectionState.DISCONNECTED)){
//                            if (haveInternet() == true) {
//                                connectXmpp();
//                                etHistory.append("reconectando Xmpp");
//                            } else {
//                                disconnectXmpp();
//                                etHistory.append("Sin internet");
//                            }
//                        }
                        break;
                    default:
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter(XmppService.UPDATE_CONNECTION);
        filter.addAction(XmppService.NEW_MESSAGE);
        filter.addAction(XmppService.CHANGE_CONNECTIVITY);

        if(registeredBR == false){
            try {
                registerReceiver(wBReceiver, filter);
                registeredBR = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void connectXmpp(){
        Log.i("ConnectXmpp",XmppService.getState().toString());
        if(XmppService.getState().equals(XmppConnection.ConnectionState.DISCONNECTED)){
            if(haveInternet() == true) {
                Intent intent = new Intent(this, XmppService.class);
                this.startService(intent);
            }
        }
    }

    private void disconnectXmpp(){
        Log.i("disconnectXmpp",XmppService.getState().toString());
        if(XmppService.getState().equals(XmppConnection.ConnectionState.CONNECTED) ||
                XmppService.getState().equals(XmppConnection.ConnectionState.AUTHENTICATE)){
            Intent intent = new Intent(this, XmppService.class);
            this.stopService(intent);
            registeredBR = false;
        }
    }

    public void btnOnConnect(View v){
        if(userConnection == false){
            drawBtnGreen("Conectando");
            userConnection = true;
            connectXmpp();
        }else {
            drawBtnRed("Desconectado");
            userConnection = false;
            disconnectXmpp();
        }
    }

    public void drawBtnRed(String msg){
        btnConnect.setText(msg);
        btnConnect.setTextColor(Color.argb(255,255,0,0));
    }

    public void drawBtnGreen(String msg){
        btnConnect.setText(msg);
        btnConnect.setTextColor(Color.argb(255,0,255,0));
    }

//    private void sendSMSIntent(String number, String Code){
//        Log.i(SEND_SMS, "Enviar SMS");
//        Intent smsIntent = new Intent(Intent.ACTION_VIEW);
//
//        smsIntent.setData(Uri.parse("smsto:"));
//        smsIntent.setType("vnd.android-dir/mms-sms");
//        smsIntent.putExtra("address" , new String (number));
//        smsIntent.putExtra("sms_body", Const.MENSAJE + Code);
//        try{
//            startActivity(smsIntent);
//            Log.i(SEND_SMS, "Enviando SMS");
//            finish();
//        }catch (android.content.ActivityNotFoundException e){
//            etHistory.append("\nNo es posible enviar el SMS");
//        }
//    }

    private void sendSMS(String to, String code){
        try {
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(to, null, etMsg.getText() + ":" +code, sentPI, deliveredPI);
            etHistory.append("\nIntentando enviar SMS");
        }catch(Exception e){
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "Fallo el SMS (permiso?)", Toast.LENGTH_SHORT).show();
        }
    }
    private boolean testCellMsg(String number, String message){
        if(number.length() != Const.SIZE_CELL_NUMBER) return false;
        if(number.isEmpty()) return false;
        if(message.length() != Const.SIZE_CODE_NUMBER) return false;
        if(message.isEmpty()) return false;
        return true;
    }

    private boolean testXmppMsg(String message){
        if(message.isEmpty()) return false;
        if(message.length() < 17) return false;
        if(message.contains("@") == false)return false;
        return true;
    }

    private boolean haveInternet(){
        ConnectivityManager cm =
                (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if(activeNetwork == null)
            return false;
        else
            return true;
    }
//    Runnable runnable = new Runnable() {
//        @Override
//        public void run() {
//            Set <String> keySet = cola.keySet();
//            Iterator<String> iterator = keySet.iterator();
//            Log.i("Timer","Se ejecuto el Timer" + iterator.hasNext());
//            try{
//                updateStatus();
//            }finally {
//                if(iterator.hasNext()){
//                    String key = iterator.next();
//                    String code = cola.get(key);
//                    if((code.isEmpty()) || (code.length() != SIZE_CODE))
//                        return;
//                    //sendSMS(key,code);
//                    etHistory.append("\nSMS a:" + key + " Codigo:" + code);
//                    cola.remove(key);
//                }
//                handler.postDelayed(runnable, interval);
//            }
//        }
//    };
//
//    void startRepeatingTask(){
//        runnable.run();
//    }
//
//    void stopRepeatingTask(){
//        handler.removeCallbacks(runnable);
//    }
//
//
//    void updateStatus(){
//    }
//
//    public void btnSendSMS(View v) {
//        String noCelular, mensaje;
//        noCelular = etCell.getText().toString();
//        mensaje = etMsg.getText().toString();
//        if (testCellMsg(noCelular, mensaje) == false) return;
//        etHistory.append("\nManual>" + noCelular + ":" + mensaje);
//        sendSMS(noCelular, mensaje);
//    }
}

