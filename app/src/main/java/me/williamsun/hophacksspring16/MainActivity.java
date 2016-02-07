package me.williamsun.hophacksspring16;

import android.app.Activity;
import android.app.NotificationManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandIOException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.BandAccelerometerEvent;
import com.microsoft.band.sensors.BandAccelerometerEventListener;
import com.microsoft.band.sensors.BandAltimeterEvent;
import com.microsoft.band.sensors.BandAltimeterEventListener;
import com.microsoft.band.sensors.BandGsrEvent;
import com.microsoft.band.sensors.BandGsrEventListener;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
import com.microsoft.band.sensors.BandRRIntervalEvent;
import com.microsoft.band.sensors.BandRRIntervalEventListener;
import com.microsoft.band.sensors.HeartRateConsentListener;
import com.microsoft.band.sensors.SampleRate;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

    private BandClient client = null;
    private TextView heartStatus, accelX, accelY, accelZ, altimeterStatus, GSRStatus, RRStatus, errorStatus;

    private final String LOG_TAG = MainActivity.class.getSimpleName();
    SmsManager smsManager;

    public enum SensorData {
        HEART_RATE, ACCELEROMETER, ALTIMETER, GSR, RR_INTERVAL, ERROR_TEXT
    }

    public enum HealthEvents {
        FALL, CRASH, CARDIAC_ARREST
    }

    private BandHeartRateEventListener mHeartRateEventListener = new BandHeartRateEventListener() {
        @Override
        public void onBandHeartRateChanged(final BandHeartRateEvent event) {
            if (event != null) {
                appendToUI(SensorData.HEART_RATE, "" + event.getHeartRate());
            }
        }
    };

    private BandAccelerometerEventListener mAccelerometerEventListener = new BandAccelerometerEventListener() {
        @Override
        public void onBandAccelerometerChanged(final BandAccelerometerEvent event) {
            if (event != null) {
                appendToUI(SensorData.ACCELEROMETER, event.getAccelerationX() + " " +
                        event.getAccelerationY() + " "  + event.getAccelerationZ());
            }
        }
    };

    private BandAltimeterEventListener mAltimeterEventListener = new BandAltimeterEventListener() {
        @Override
        public void onBandAltimeterChanged(final BandAltimeterEvent event) {
            if (event != null) {
                appendToUI(SensorData.ALTIMETER, event.getRate() + "");
            }
        }
    };


    private BandGsrEventListener mGsrEventListener = new BandGsrEventListener() {
        @Override
        public void onBandGsrChanged(final BandGsrEvent event) {
            if (event != null) {
                appendToUI(SensorData.GSR, "" + event.getResistance());
            }
        }
    };

    private BandRRIntervalEventListener mRRIntervalEventListener = new BandRRIntervalEventListener() {
        @Override
        public void onBandRRIntervalChanged(final BandRRIntervalEvent event) {
            if (event != null) {
                appendToUI(SensorData.RR_INTERVAL, "" + event.getInterval());
            }
        }
    };

    NotificationCompat.Builder mBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        smsManager = SmsManager.getDefault();

        //But do I actually need a FAB
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendAlert(HealthEvents.CARDIAC_ARREST);
            }
        });

        heartStatus = (TextView) findViewById(R.id.heartRateText);
        accelX = (TextView) findViewById(R.id.accelX);
        accelY = (TextView) findViewById(R.id.accelY);
        accelZ = (TextView) findViewById(R.id.accelZ);
        altimeterStatus = (TextView) findViewById(R.id.altimeterText);
        GSRStatus = (TextView) findViewById(R.id.GSRText);
        RRStatus = (TextView) findViewById(R.id.RRIntervalText);

        final WeakReference<Activity> reference = new WeakReference<Activity>(this);
        new HeartRateSubscriptionTask().execute();
        new HeartRateConsentTask().execute(reference);
        new AccelerometerSubscriptionTask().execute();
        new AltimeterSubscriptionTask().execute();
        new GsrSubscriptionTask().execute();
        new RRIntervalSubscriptionTask().execute();
    }


    @Override
    protected void onResume() {
        super.onResume();
        heartStatus.setText("");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onPause() {
        super.onPause();

        //Comment out if you want it to keep running
        if (client != null) {
            try {
                client.getSensorManager().unregisterHeartRateEventListener(mHeartRateEventListener);
            } catch (BandIOException e) {
                appendToUI(SensorData.ERROR_TEXT, e.getMessage());
            }
        }
    }


    @Override
    protected void onDestroy() {
        if (client != null) {
            try {
                client.disconnect().await();
            } catch (InterruptedException e) {
                // Do nothing as this is happening during destroy
            } catch (BandException e) {
                // Do nothing as this is happening during destroy
            }
        }
        super.onDestroy();
    }


    private class HeartRateSubscriptionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    client.getSensorManager().registerHeartRateEventListener(mHeartRateEventListener);
                } else {
                    appendToUI(SensorData.ERROR_TEXT, "Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occurred: " + e.getMessage() + "\n";
                        break;
                }
                appendToUI(SensorData.ERROR_TEXT, exceptionMessage);

            } catch (Exception e) {
                appendToUI(SensorData.ERROR_TEXT, e.getMessage());
            }
            return null;
        }
    }

    private class AccelerometerSubscriptionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    appendToUI(SensorData.ERROR_TEXT, "Band is connected.\n");
                    client.getSensorManager().registerAccelerometerEventListener(mAccelerometerEventListener, SampleRate.MS128);
                } else {
                    appendToUI(SensorData.ERROR_TEXT, "Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage() + "\n";
                        break;
                }
                appendToUI(SensorData.ERROR_TEXT, exceptionMessage);

            } catch (Exception e) {
                appendToUI(SensorData.ERROR_TEXT, e.getMessage());
            }
            return null;
        }
    }


    private class AltimeterSubscriptionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    int hardwareVersion = Integer.parseInt(client.getHardwareVersion().await());
                    if (hardwareVersion >= 20) {
                        appendToUI(SensorData.ERROR_TEXT, "Band is connected.\n");
                        client.getSensorManager().registerAltimeterEventListener(mAltimeterEventListener);
                    } else {
                        appendToUI(SensorData.ERROR_TEXT, "The Altimeter sensor is not supported with your Band version. Microsoft Band 2 is required.\n");
                    }
                } else {
                    appendToUI(SensorData.ERROR_TEXT, "Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage() + "\n";
                        break;
                }
                appendToUI(SensorData.ERROR_TEXT, exceptionMessage);

            } catch (Exception e) {
                appendToUI(SensorData.ERROR_TEXT, e.getMessage());
            }
            return null;
        }
    }


    private class GsrSubscriptionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    int hardwareVersion = Integer.parseInt(client.getHardwareVersion().await());
                    if (hardwareVersion >= 20) {
                        appendToUI(SensorData.ERROR_TEXT, "Band is connected.\n");
                        client.getSensorManager().registerGsrEventListener(mGsrEventListener);
                    } else {
                        appendToUI(SensorData.ERROR_TEXT, "The Gsr sensor is not supported with your Band version. Microsoft Band 2 is required.\n");
                    }
                } else {
                    appendToUI(SensorData.ERROR_TEXT, "Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage() + "\n";
                        break;
                }
                appendToUI(SensorData.ERROR_TEXT, exceptionMessage);

            } catch (Exception e) {
                appendToUI(SensorData.ERROR_TEXT, e.getMessage());
            }
            return null;
        }
    }


    private class HeartRateConsentTask extends AsyncTask<WeakReference<Activity>, Void, Void> {
        @Override
        protected Void doInBackground(WeakReference<Activity>... params) {
            try {
                if (getConnectedBandClient()) {
                    if (true) {
                    //if (params[0].get() != null) {
                        client.getSensorManager().requestHeartRateConsent(params[0].get(), new HeartRateConsentListener() {
                            @Override
                            public void userAccepted(boolean consentGiven) {
                            }
                        });
                    }
                } else {
                    appendToUI(SensorData.ERROR_TEXT, "Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occurred: " + e.getMessage() + "\n";
                        break;
                }
                appendToUI(SensorData.ERROR_TEXT, exceptionMessage);

            } catch (Exception e) {
                appendToUI(SensorData.ERROR_TEXT, e.getMessage());
            }
            return null;
        }
    }


    private class RRIntervalSubscriptionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    int hardwareVersion = Integer.parseInt(client.getHardwareVersion().await());
                    if (hardwareVersion >= 20) {
                        if (client.getSensorManager().getCurrentHeartRateConsent() == UserConsent.GRANTED) {
                            client.getSensorManager().registerRRIntervalEventListener(mRRIntervalEventListener);
                        } else {
                            appendToUI(SensorData.ERROR_TEXT, "You have not given this application consent to access heart rate data yet."
                                    + " Please press the Heart Rate Consent button.\n");
                        }
                    } else {
                        appendToUI(SensorData.ERROR_TEXT, "The RR Interval sensor is not supported with your Band version. Microsoft Band 2 is required.\n");
                    }
                } else {
                    appendToUI(SensorData.ERROR_TEXT, "Band isn't connected. Please make sure bluetooth is on and the band is in range.\n");
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage() + "\n";
                        break;
                }
                appendToUI(SensorData.ERROR_TEXT, exceptionMessage);

            } catch (Exception e) {
                appendToUI(SensorData.ERROR_TEXT, e.getMessage());
            }
            return null;
        }
    }

    private void sendAlert(HealthEvents he){

        String phoneNumber = "+19149604822"; //Bilal's phone number
        String message = "Will's health is in poor condition";

        if(he.equals(HealthEvents.CARDIAC_ARREST)){
            mBuilder =
                    new NotificationCompat.Builder(this)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("Cardiac arrest")
                            .setContentText("Risk of cardiac arrest detected");
            message += "... a cardiac arrest has been detected";
        } else if (he.equals(HealthEvents.CRASH)){
            mBuilder =
                    new NotificationCompat.Builder(this)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("Car crash")
                            .setContentText("Car crash detected");
            message += "... a vehicular crash has been detected";
        } else if (he.equals(HealthEvents.FALL)){
            mBuilder =
                    new NotificationCompat.Builder(this)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("Fall")
                            .setContentText("Fall detected");
            message += "... a fall has been detected";
        }
        // Sets an ID for the notification
        int mNotificationId = 001;

        // Gets an instance of the NotificationManager service
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        //Get alarm sound and set it
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        mBuilder.setSound(alarmSound);

        // Builds the notification and issues it.
        mNotifyMgr.notify(mNotificationId, mBuilder.build());

        smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
    }


    //Appends appropriate value to UI
    private void appendToUI(final SensorData sd, final String string) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String toAdd = string;
                if(sd.equals(SensorData.HEART_RATE)){
                    heartStatus.setText(string);
                } else if(sd.equals(SensorData.ACCELEROMETER)){
                    String[] s = string.split(" ");
                    if(s[0].length() < 4){
                        s[0] += "    ";
                    }
                    if(s[1].length() < 4){
                        s[1] += "    ";
                    }
                    if(s[2].length() < 4){
                        s[2] += "    ";
                    }
                    accelX.setText("X: " + s[0].substring(0, 4));
                    accelY.setText("Y: " + s[1].substring(0, 4));
                    accelZ.setText("Z: " + s[2].substring(0, 4));
                } else if(sd.equals(SensorData.ALTIMETER)){
                    altimeterStatus.setText(string);
                } else if(sd.equals(SensorData.GSR)){
                    GSRStatus.setText(string);
                } else if(sd.equals(SensorData.RR_INTERVAL)){

                    if(toAdd.length() < 4){
                        toAdd += "    ";
                    }
                    RRStatus.setText(toAdd.substring(0, 4));
                } else if (sd.equals(SensorData.ERROR_TEXT)){
                    // TODO MAKE A SNACKBAR
                }
            }
        });
    }

    private boolean getConnectedBandClient() throws InterruptedException, BandException {
        if (client == null) {
            BandInfo[] devices = BandClientManager.getInstance().getPairedBands();
            if (devices.length == 0) {
                appendToUI(SensorData.ERROR_TEXT, "Band isn't paired with your phone.\n");
                return false;
            }
            client = BandClientManager.getInstance().create(getBaseContext(), devices[0]);
        } else if (ConnectionState.CONNECTED == client.getConnectionState()) {
            return true;
        }

        appendToUI(SensorData.ERROR_TEXT, "Band is connecting...\n");
        return ConnectionState.CONNECTED == client.connect().await();
    }
}
