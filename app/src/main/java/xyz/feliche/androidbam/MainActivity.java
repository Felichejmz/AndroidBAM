package xyz.feliche.androidbam;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
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
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;

import servicio.XmppConnection;
import servicio.XmppService;

public class MainActivity extends AppCompatActivity {
    private EditText etHistory;
    private EditText etMsg;
    private Button btnConnect;
    private TextView tvVersion;
    private TextView tvCuenta;
    private TextView tvUso;

    WakefulBroadcastReceiver wBReceiver;
    boolean registeredBR = false;

    String accountXmpp, passXmpp;

    private static final String MAINTAG = "ON PRINCIPAL:";
    private static final String ON_RESUME = "ON RESUME:";

    public static boolean userConnection = false;

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
        tvVersion = (TextView)findViewById(R.id.tvVersion);
        tvCuenta = (TextView)findViewById(R.id.tvAccountServerSMS);
        tvUso = (TextView)findViewById(R.id.tvInstrucciones);

        accountXmpp = Const.SERVER_SMS_ACCOUNT;
        passXmpp = Const.SERVER_SMS_PASS;

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        tvVersion.setText(Const.VERSION);
        tvCuenta.setText(Const.SERVER_SMS_ACCOUNT + "@" + Const.SERVER_NAME);
        tvUso.setText("NúmeroCelular" + Const.SEPARADOR + "Código 6 digitos");

        //handler = new Handler();
        //startRepeatingTask();
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // reinicio el editText al tener muchas lineas
                clearHistory();;
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
                // reinicio el editText
                clearHistory();
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

        // reinicio el editText
        clearHistory();
        if(XmppService.getState().equals(XmppConnection.ConnectionState.DISCONNECTED)){
            etHistory.setText("Desconectado");
            drawBtnRed("Desconectado");
        }else if(XmppService.getState().equals(XmppConnection.ConnectionState.CONNECTED)){
            etHistory.setText("Conectado");
            drawBtnGreen("Conectado");
        }
        etHistory.setText("Iniciando.........");
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
                // reinicio el editText
                clearHistory();
                String action = intent.getAction();
                etHistory.append("\n" + action + Calendar.getInstance().get(Calendar.HOUR) +
                                ":" + Calendar.getInstance().get(Calendar.MINUTE));
                switch (action) {
                    case XmppService.BUNDLE_ROSTER:
                        ArrayList<String> lista;
                        lista = intent.getStringArrayListExtra(XmppService.LIST_ROSTER);
                        for(String s : lista)
                            etHistory.append("\n " + s);
                        break;
                    case XmppService.NEW_MESSAGE:
                        String message = intent.getStringExtra(XmppService.BUNDLE_MESSAGE_BODY);
                        Log.e(ON_RESUME, "Nuevo mensaje Xmpp:");
                        etHistory.append("\n" + "Nuevo mensaje Xmpp:" + message);
                        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                        vibrator.vibrate(1000);
                        if (testXmppMsg(message) == false) break;
                        String numero = message.split(Const.SEPARADOR)[0];
                        String codigo = message.split(Const.SEPARADOR)[1];
                        if (testCellMsg(numero, codigo) == false) break;
                        //Agrega solo si no esta en la lista el numero
                        // y no se puede quitar el limite de SMS's
                        //if(!cola.containsKey(numero)) {
                        //    cola.put(numero, codigo);
                        //    vibrator.vibrate(500);
                        //}
                        sendSMS(numero, codigo);
                        break;
                    // Estado de la conexión XMPP
                    case XmppService.UPDATE_CONNECTION:
                        String status = intent.getStringExtra(XmppService.CONNECTION);
                        Log.e(ON_RESUME, "XMPP update connection: " + status);
                        etHistory.append("\n" + "XMPP update connection: " + status);
                        if (status.equals("AUTHENTICATE") || status.equals("RECONNECTED")) {
                            drawBtnGreen("En linea");
                            etHistory.append("\n" + "en linea");
                        } else if(status.equals("CONNECTED")){
                            drawBtnGreen("Conectado");
                        } else if (status.equals("DISCONNECTED")) {
                            drawBtnRed("Desconectado");
                        } else if (status.equals("CLOSED_ERROR")) {
                            reconnect();
                            drawBtnRed("Error");
                        } else if(status.equals("HOSTNAME_ERROR")){
                            reconnect();
                            drawBtnRed("Error HOSTNAME");
                        } else if(status.equals("AUTH_ERROR")) {
                            reconnect();
                            drawBtnRed("Error AUTH");
                        } else if(status.equals("IO_ERROR")){
                            reconnect();
                            drawBtnRed("Error IO");
                        }
                        break;

                    // Cambio de la conexión a Internet
                    case XmppService.CHANGE_CONNECTIVITY:
                        Log.e(ON_RESUME, "XMPP change connectivity: ");
                        etHistory.append("\n XMPP change connectivity : " + " userConection : " + userConnection);
                        reconnect();
//                        if(userConnection == true){
//                            if(haveInternet() == true) {
//                                connectXmpp();
//                            }else{
//                                disconnectXmpp();
//                            }
//                        }else {
//                            drawBtnRed("Cambio de red");
//                        }
//                        if(haveInternet() == true)
//                            connectXmpp();
//                        else
//                            disconnectXmpp();
//                        break
                    default:
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter(XmppService.UPDATE_CONNECTION);
        filter.addAction(XmppService.NEW_MESSAGE);
        filter.addAction(XmppService.BUNDLE_ROSTER);
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

    private void reconnect(){
        if(MainActivity.userConnection == true) {
            Intent intent = new Intent(this, XmppService.class);
            this.stopService(intent);
            this.startService(intent);
        }
    }

    private void connectXmpp(){
        Intent intent = new Intent(this, XmppService.class);
        this.startService(intent);
        etHistory.append("\n connectXMPP: Conectando....");
//        if(XmppService.getState().equals(XmppConnection.ConnectionState.DISCONNECTED)||
//                XmppService.getState().equals(XmppConnection.ConnectionState.CLOSED_ERROR)){
//            if(haveInternet() == true) {
//                Intent intent = new Intent(this, XmppService.class);
//                this.startService(intent);
//                etHistory.append("\n connectXMPP: Conectando....");
//            }else{
//                etHistory.append("\n connectXMPP: Sin conexion a Internet");
//            }
//        }
    }

    private void disconnectXmpp(){
        Intent intent = new Intent(this, XmppService.class);
        this.stopService(intent);
        etHistory.append("\n disconnectXMPP: desconectando....");
//        if(status.equals(XmppConnection.ConnectionState.CONNECTED.toString()) ||
//                status.equals(XmppConnection.ConnectionState.AUTHENTICATE.toString())||
//                status.equals(XmppConnection.ConnectionState.RECONNECTED.toString())){
//            Intent intent = new Intent(this, XmppService.class);
//            this.stopService(intent);
//            etHistory.append("\n disconnectXMPP: desconectando....");
//        }else{
//            etHistory.append("\n disconnectXMPP: No existen conexiones para desconectar");
//        }
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

    @Override
    public void onBackPressed(){
        Log.d(MAINTAG,"key back press");
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
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
            Toast.makeText(getApplicationContext(), "Fallo (Se otorgo permiso de enviar SMS's?)",
                    Toast.LENGTH_SHORT).show();
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
        // numero celular + codigo + separador
        if(message.length() < Const.SIZE_CELL_NUMBER +
                Const.SIZE_CODE_NUMBER + 1) return false;
        if(message.contains(Const.SEPARADOR) == false)return false;
        return true;
    }

//    private boolean haveInternet(){
//        ConnectivityManager cm =
//                (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);
//        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
//        if(activeNetwork == null)
//            return false;
//        else
//            return true;
//    }

    private void clearHistory(){
        if(etHistory.getLineCount() >= Const.MAXLINES){
            etHistory.setText("");
        }
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

