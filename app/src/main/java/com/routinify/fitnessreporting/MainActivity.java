package com.routinify.fitnessreporting;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHealth;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.amplify.generated.graphql.CreateFitnessDataMutation;
import com.amazonaws.amplify.generated.graphql.CreateLastUpdatedTimeMutation;
import com.amazonaws.amplify.generated.graphql.GetLastUpdatedTimeQuery;
import com.amazonaws.amplify.generated.graphql.ListFitnessDataQuery;
import com.amazonaws.amplify.generated.graphql.ListLastUpdatedTimesQuery;
import com.amazonaws.amplify.generated.graphql.UpdateLastUpdatedTimeMutation;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient;
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers;
import com.apollographql.apollo.GraphQLCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.fitpolo.support.MokoConstants;
import com.fitpolo.support.MokoSupport;
import com.fitpolo.support.callback.MokoScanDeviceCallback;
import com.fitpolo.support.entity.BleDevice;
import com.fitpolo.support.handler.MokoLeScanHandler;


import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;


import butterknife.ButterKnife;
import type.CreateLastUpdatedTimeInput;
import type.TableLastUpdatedTimeFilterInput;
import type.TableStringFilterInput;
import type.UpdateLastUpdatedTimeInput;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;


public class MainActivity extends AppCompatActivity implements MokoScanDeviceCallback {
    private AWSAppSyncClient mAWSAppSyncClient;
    private String lastUpdatedString;
    private String lastUpdatedId;
    private boolean processing;
    private BluetoothHealth smartWatch;
    private static final UUID SERVICE_UUID = UUID.fromString("0000ffc0-0000-1000-8000-00805f9b34fb");
    private static final long SCAN_PERIOD = 5000;

    private ArrayList<BleDevice> mDatas;
    //private DeviceAdapter mAdapter;
    private ProgressDialog mDialog;
    private MokoService mService;
    private BleDevice mDevice;
    private HashMap<String, BleDevice> deviceMap;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*
        This builds the appsync client
         */
        mAWSAppSyncClient = AWSAppSyncClient.builder()
                .context(getApplicationContext())
                .awsConfiguration(new AWSConfiguration(getApplicationContext()))
                .build();

        ButterKnife.bind(this);
        bindService(new Intent(this, MokoService.class), mServiceConnection, BIND_AUTO_CREATE);
        mDialog = new ProgressDialog(this);
        mDatas = new ArrayList<>();
        deviceMap = new HashMap<>();

        MokoSupport.getInstance().init(this.getApplicationContext());
        //bluetoothAdapter.getProfileProxy(getApplicationContext(), profileListener, BluetoothProfile.HEALTH);

        //IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        //registerReceiver(mReceiver, filter);

    }

    public void scan(View view){

        MokoSupport.getInstance().startScanDevice(this);
    }



    private BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HEALTH) {
                smartWatch = (BluetoothHealth) proxy;
            }
        }
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HEADSET) {
                smartWatch = null;
            }
        }
    };

    // Create a BroadcastReceiver for ACTION_FOUND.
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                if (MokoConstants.ACTION_DISCOVER_SUCCESS.equals(intent.getAction())) {
                    abortBroadcast();
                    if (!MainActivity.this.isFinishing() && mDialog.isShowing()) {
                        mDialog.dismiss();
                    }
                    Toast.makeText(MainActivity.this, "Connect success", Toast.LENGTH_SHORT).show();
                    //Intent orderIntent = new Intent(MainActivity.this, SendOrderActivity.class);
                   // orderIntent.putExtra("device", mDevice);
                    //startActivity(orderIntent);
                }
                if (MokoConstants.ACTION_CONN_STATUS_DISCONNECTED.equals(intent.getAction())) {
                    abortBroadcast();
                    if (MokoSupport.getInstance().isBluetoothOpen() && MokoSupport.getInstance().getReconnectCount() > 0) {
                        return;
                    }
                    if (!MainActivity.this.isFinishing() && mDialog.isShowing()) {
                        mDialog.dismiss();
                    }
                    Toast.makeText(MainActivity.this, "Connect failed", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((MokoService.LocalBinder) service).getService();
            // 注册广播接收器
            IntentFilter filter = new IntentFilter();
            filter.addAction(MokoConstants.ACTION_CONN_STATUS_DISCONNECTED);
            filter.addAction(MokoConstants.ACTION_DISCOVER_SUCCESS);
            filter.setPriority(100);
            registerReceiver(mReceiver, filter);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(mReceiver);
    }



    public void findLastUpdated(View view){

        /*
        Getting the strings from the entry fields
         */
        Spinner dataTypeSpinner = findViewById(R.id.spinner);
        String dataType = dataTypeSpinner.getSelectedItem().toString();
        TextView memberId = findViewById(R.id.memberText);
        String memberIdText = memberId.getText().toString();

        //Runs the query to find the last update time for a fitness data type
        processing = true;
        runQuery(memberIdText,dataType);

        while(processing){

        }
        //displays the last updated time
        if(lastUpdatedString!=null){
            TextView lastUpdatedText = findViewById(R.id.lastUpdatedText);
            //lastUpdatedText.setText(lastUpdatedString);
        }

        ConnectivityManager cm =
                (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        if (!isConnected) {
            finish();
        }
    }

    public void sync(View view){
        /*
        Getting the strings from the entry fields
         */
        Spinner dataTypeSpinner = findViewById(R.id.spinner);
        String dataType = dataTypeSpinner.getSelectedItem().toString();
        TextView memberId = findViewById(R.id.memberText);
        String memberIdText = memberId.getText().toString();

        //Runs the query to find the last update time for a fitness data type
        processing = true;
        runQuery(memberIdText,dataType);

        while(processing){

        }

            Calendar currentCal = Calendar.getInstance();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            String currentTimeString = formatter.format(currentCal.getTime());
            currentTimeString = currentTimeString.substring(0, 23);
            currentTimeString = currentTimeString.concat("Z");
            Log.d("DateTime:", currentTimeString);
        if(lastUpdatedString!=null) {
            runUpdateMutation(memberIdText, currentTimeString, dataType);
        }
        else{
            runCreateUpdateMutation(memberIdText,currentTimeString,dataType);
        }
        ConnectivityManager cm =
                (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        if (!isConnected) {
            finish();
        }
    }

    public void runUpdateMutation(String member,String timestamp,String type){
        UpdateLastUpdatedTimeInput updateLastUpdatedTimeInput = UpdateLastUpdatedTimeInput.builder().
                id(lastUpdatedId).
                member(member).
                timestamp(timestamp).
                type(type).
                build();
        Log.d("Mutation",updateLastUpdatedTimeInput.toString());
        processing = true;
        mAWSAppSyncClient.mutate(UpdateLastUpdatedTimeMutation.builder().input(updateLastUpdatedTimeInput).build())
                .enqueue(mutationCallback);
        while(processing){

        }
        Log.d("Mutation","Mutation finished");
    }

    private GraphQLCall.Callback<UpdateLastUpdatedTimeMutation.Data> mutationCallback = new GraphQLCall.Callback<UpdateLastUpdatedTimeMutation.Data>() {
        @Override
        public void onResponse(@Nonnull Response<UpdateLastUpdatedTimeMutation.Data> response) {

                    Log.i("Results", "Updated Last Update Time");
                    processing = false;
                    finish();



        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e("Error", e.toString());
            processing = false;
        }
    };

    /*
    Constructs the query and sends it to the database using the appsync client
     */
    public void runQuery(@Nonnull String member,@Nonnull String type){
        TableStringFilterInput memberFilter = TableStringFilterInput.builder().eq(member).build();
        TableStringFilterInput typeFilter = TableStringFilterInput.builder().eq(type).build();
        TableLastUpdatedTimeFilterInput lastUpdatedFilter = TableLastUpdatedTimeFilterInput.builder()
                .member(memberFilter)
                .type(typeFilter)
                .build();
        mAWSAppSyncClient.query(ListLastUpdatedTimesQuery.builder()
                .filter(lastUpdatedFilter)
                .build())
                .responseFetcher(AppSyncResponseFetchers.CACHE_AND_NETWORK)
                .enqueue(lastUpdatedTimeCallback);

        Log.d("Query", ListLastUpdatedTimesQuery.builder()
                .filter(lastUpdatedFilter)
                .build().variables().filter().member()
                .toString());
        Log.d("Query",member+"\n"+type);
    }

    private GraphQLCall.Callback<ListLastUpdatedTimesQuery.Data> lastUpdatedTimeCallback = new GraphQLCall.Callback<ListLastUpdatedTimesQuery.Data>() {
        @Override
        public void onResponse(@Nonnull final Response<ListLastUpdatedTimesQuery.Data> response) {
            Log.d("Query","Got here");


            if(response.data().listLastUpdatedTimes().items().size()!=0) {

                Log.i("Results", "\n\nID:"+response.data().listLastUpdatedTimes().items().get(0).id() + "\n" + response.data().listLastUpdatedTimes().items().get(0).timestamp()+"\n\n\n");
                lastUpdatedString = response.data().listLastUpdatedTimes().items().get(0).timestamp();
                lastUpdatedId = response.data().listLastUpdatedTimes().items().get(0).id();
                processing = false;
            }
            else {
                Log.d("Results","Null result");
                processing = false;
            }
            finish();
        }




        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e("ERROR", e.toString());
        }
    };

    public void runCreateUpdateMutation(String member,String timestamp,String type){
        CreateLastUpdatedTimeInput createLastUpdatedTimeInput = CreateLastUpdatedTimeInput.builder().
                member(member).
                timestamp(timestamp).
                type(type).
                build();
        Log.d("Mutation",createLastUpdatedTimeInput.toString());
        processing = true;
        mAWSAppSyncClient.mutate(CreateLastUpdatedTimeMutation.builder().input(createLastUpdatedTimeInput).build())
                .enqueue(createMutationCallback);
        while(processing){

        }
        Log.d("Mutation","Mutation finished");
    }

    private GraphQLCall.Callback<CreateLastUpdatedTimeMutation.Data> createMutationCallback = new GraphQLCall.Callback<CreateLastUpdatedTimeMutation.Data>() {
        @Override
        public void onResponse(@Nonnull Response<CreateLastUpdatedTimeMutation.Data> response) {


                    Log.i("Results", "Updated Last Update Time");
                    processing = false;
                    finish();



            //Log.d("Mutation",response.data().updateLastUpdatedTime().toString());
        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e("Error", e.toString());
            processing = false;
        }
    };

    @Override
    public void onStartScan() {
        deviceMap.clear();
        mDialog.setMessage("Scanning...");
        mDialog.show();
    }

    @Override
    public void onScanDevice(BleDevice device) {
        deviceMap.put(device.address, device);
        mDatas.clear();
        mDatas.addAll(deviceMap.values());
        //mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onStopScan() {
        if (!MainActivity.this.isFinishing() && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        mDatas.clear();
        mDatas.addAll(deviceMap.values());
        Log.d("Bluetooth","Finished Scanning");
        //mAdapter.notifyDataSetChanged();
    }
}
