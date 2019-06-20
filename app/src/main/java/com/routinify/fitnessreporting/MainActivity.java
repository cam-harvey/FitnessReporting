package com.routinify.fitnessreporting;

import android.Manifest;
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
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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
import com.fitpolo.support.entity.DailySleep;
import com.fitpolo.support.entity.HeartRate;
import com.fitpolo.support.entity.Step;
import com.fitpolo.support.handler.MokoLeScanHandler;
import com.fitpolo.support.task.ZIntervalStepReadTask;
import com.fitpolo.support.task.ZReadBatteryTask;
import com.fitpolo.support.task.ZReadHeartRateIntervalTask;
import com.fitpolo.support.task.ZReadHeartRateTask;
import com.fitpolo.support.task.ZReadSleepGeneralTask;
import com.fitpolo.support.task.ZReadStepInterval;
import com.fitpolo.support.task.ZReadStepTask;
import com.fitpolo.support.task.ZWriteHeartRateIntervalTask;
import com.fitpolo.support.task.ZWriteShakeTask;
import com.fitpolo.support.task.ZWriteStepIntervalTask;
import com.fitpolo.support.task.ZWriteSystemTimeTask;


import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.UUID;

import javax.annotation.Nonnull;


import butterknife.ButterKnife;
import type.CreateFitnessDataInput;
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
    private String deviceMAC;
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
    private ArrayList<CreateFitnessDataInput> fitnessData;
    private ArrayList<Step> mySteps;


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
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.ACCESS_COARSE_LOCATION}, AppConstants.PERMISSION_REQUEST_CODE);

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
        processing = true;
        MokoSupport.getInstance().startScanDevice(this);



    }

    public void connect(View view){
        mDialog.setMessage("Connect...");
        mDialog.show();

        TextView memberId = findViewById(R.id.memberText);
        String memberIdText = memberId.getText().toString();

        processing = true;
        runQuery(memberIdText,null);

        while(processing){
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if(deviceMAC!=null) {
            //Device query for mac address
            BleDevice device = null;
            for (BleDevice bleDevice : mDatas) {
                if (bleDevice.address.equals(deviceMAC))
                    device = bleDevice;
            }
            if(device!=null) {
                mService.connectBluetoothDevice(device.address);
                mDevice = device;
            }
        }
    }



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
                    Intent orderIntent = new Intent(MainActivity.this, SyncActivity.class);
                   orderIntent.putExtra("device", mDevice);
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
            Log.d("StartUp","Failure");
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(mReceiver);
        unbindService(mServiceConnection);
        stopService(new Intent(this, MokoService.class));
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
    //Sync data with watch
    public void sync(View view) {
        /*
        Getting the strings from the entry fields
         */
        Spinner dataTypeSpinner = findViewById(R.id.spinner);
        final String dataType = dataTypeSpinner.getSelectedItem().toString();
        TextView memberId = findViewById(R.id.memberText);
        final String memberIdText = memberId.getText().toString();

        //Runs the query to find the last update time for a fitness data type
        processing = true;
        runQuery(memberIdText, dataType);

        while (processing) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        /*
        Setting up current battery to pass to the update
         */

        MokoSupport.getInstance().setProcessing(true);
        batteryRead();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (MokoSupport.getInstance().getProcessing()) {
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
                finishSync(dataType,memberIdText);
                //Thread.join();
            }
        }).start();

    }
        public void finishSync(String dataType,String memberIdText){

            Calendar currentCal = Calendar.getInstance();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            String currentTimeString = formatter.format(currentCal.getTime());
            currentTimeString = convert2ValidAWSDateTime(currentTimeString);
            Log.d("DateTime:", currentTimeString);
            //currentTimeString = "2019-06-17'T'12:00:00.000Z";
        processing = true;
        if(lastUpdatedString!=null) {
            runUpdateMutation(memberIdText, currentTimeString, dataType,mDevice.address);
        }
        else{
            runCreateUpdateMutation(memberIdText,currentTimeString,dataType,mDevice.address);
        }
        while(processing){
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
        ConnectivityManager cm =
                (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        if (!isConnected) {
            //finish();
        }
        //Calendar calendar;
        if(lastUpdatedString!=null) {
            //final Calendar calendar = Utils.strDate2Calendar(lastUpdatedString, AppConstants.PATTERN_YYYY_MM_DD_HH_MM);
        }
        else{
            //final Calendar calendar = Utils.strDate2Calendar("2000-01-01'T'00:00:00.000'Z'", AppConstants.PATTERN_YYYY_MM_DD_HH_MM);
        }
            MokoSupport.getInstance().setStepprocessing(true);
            MokoSupport.getInstance().setHeartprocessing(true);
            MokoSupport.getInstance().setSleepprocessing(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                //MokoSupport.getInstance().setProcessing(true);

                Calendar calendar = Utils.strDate2Calendar(lastUpdatedString, AppConstants.PATTERN_YYYY_MM_DD_HH_MM);

                MokoSupport.getInstance().sendOrder(new ZWriteSystemTimeTask(mService));
                MokoSupport.getInstance().sendOrder(new ZWriteStepIntervalTask(mService,1));


                MokoSupport.getInstance().sendOrder(new ZWriteHeartRateIntervalTask(mService,1));

                //MokoSupport.getInstance().sendOrder(new ZReadStepInterval(mService));

                //MokoSupport.getInstance().sendOrder(new ZReadHeartRateIntervalTask(mService));


                MokoSupport.getInstance().sendOrder(new ZIntervalStepReadTask(mService, calendar));


                MokoSupport.getInstance().sendOrder(new ZReadHeartRateTask(mService,calendar));


                MokoSupport.getInstance().sendOrder(new ZReadSleepGeneralTask(mService,calendar));



            }
        }).start();


        new Thread(new Runnable() {
            @Override
            public void run() {
                while(MokoSupport.getInstance().getProcessing()){
                    try
                    {
                        Thread.sleep(1000L);
                    }
                    catch (InterruptedException e1)
                    {
                        e1.printStackTrace();
                    }
                }
                transferSyncData();
            }
        }).start();

    }

    public void batteryRead(){

        MokoSupport.getInstance().sendOrder((new ZReadBatteryTask(mService)));
    }
    public String convert2ValidAWSDateTime(String original){
        String newString;
        original = original.substring(0, 26);
        newString = original.concat(":00");
        return newString;
    }

    public void transferSyncData(){

        //Transfer steps
        Log.d("Syncing:","Data recorded successfully");
        mySteps = MokoSupport.getInstance().getSteps();

        Step previousStep = null;

        while(MokoSupport.getInstance().getProcessing()){
            try{
                mySteps = MokoSupport.getInstance().getSteps();
                Thread.sleep(1000L);
            }catch(InterruptedException e){

            }
        }
        final String[] member = new String[1];
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView memberView = findViewById(R.id.memberText);
                member[0] = memberView.getText().toString();
            }
        });
        //if(member[0]==null) {
        //    return;
        //}


        //Tell them to enter member



        int stepValue = 0;
        List<Integer> data= new ArrayList<Integer>();
        String startTime;
        String endTime;
        Calendar currentTime;
        Calendar updateTime = Utils.strDate2Calendar(lastUpdatedString,"yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        Calendar previousTime=null;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        if(mySteps!=null)
        for(Step currentStep: mySteps){
            currentTime = Utils.strDate2Calendar(currentStep.time,"yyyy-MM-dd HH:mm");
            if(currentTime.compareTo(updateTime)>=0){
                if(previousStep!=null&& previousTime!=null){
                    startTime = convert2ValidAWSDateTime(formatter.format(previousTime.getTime()));
                    endTime = convert2ValidAWSDateTime(formatter.format(currentTime.getTime()));
                    if(previousTime.get(Calendar.HOUR_OF_DAY)==0&&previousTime.get(Calendar.MINUTE)==0){
                        stepValue = Integer.parseInt(currentStep.value);
                    }
                    else{
                        stepValue = Integer.parseInt(currentStep.value)-Integer.parseInt(previousStep.value);
                    }
                    data.add(stepValue);
                    String tempID = UUID.randomUUID().toString();
                    processing = true;
                    createFitnessDataMutation(tempID,member[0],startTime,endTime,"Steps",new ArrayList<Integer>(data));
                    while(processing){
                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    data.clear();
                }
            }
            previousStep = currentStep;
            previousTime = currentTime;
        }
        MokoSupport.getInstance().setSteps(null);
        //Transfer Sleep
        Calendar startCalendar;
        Calendar endCalendar;
        String lastRecord=null;
        int sleepType=0;
        int count=0;
        ArrayList<DailySleep> mSleep = MokoSupport.getInstance().getDailySleeps();
        if(mSleep!=null){
            for(DailySleep dailySleep:mSleep){
                startCalendar = Utils.strDate2Calendar(dailySleep.startTime,"yyyy-MM-dd HH:mm");
                endCalendar = Utils.strDate2Calendar(dailySleep.startTime,"yyyy-MM-dd HH:mm");
                for(String record:dailySleep.records){
                    if(lastRecord!=null){
                        count+=5;
                        endCalendar.add(Calendar.MINUTE,5);
                        if(!lastRecord.equals(record)) {

                            String tempID = UUID.randomUUID().toString();
                            switch (lastRecord) {
                                case "10":
                                    sleepType = 2;
                                    break;
                                case "01":
                                    sleepType = 1;
                                    break;
                            }
                            data.add(sleepType);
                            startTime = convert2ValidAWSDateTime(formatter.format(startCalendar.getTime()));
                            endTime = convert2ValidAWSDateTime(formatter.format(endCalendar.getTime()));
                            if(endCalendar.compareTo(updateTime)>=0) {
                                processing = true;
                                createFitnessDataMutation(tempID, member[0], startTime, endTime, "Sleep", new ArrayList<Integer>(data));
                                while (processing) {
                                    try {
                                        Thread.sleep(1000L);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            data.clear();
                            startCalendar.add(Calendar.MINUTE,count);
                            count = 0;
                        }
                    }
                    lastRecord=record;
                    sleepType=0;
                }
                lastRecord=null;
            }
        }
        MokoSupport.getInstance().setDailySleeps(null);
        //Transfer HeartRate
        ArrayList<HeartRate> mHeart = MokoSupport.getInstance().getHeartRates();

        if(mHeart!=null){
            for(HeartRate heartRate:mHeart){
                currentTime = Utils.strDate2Calendar(heartRate.time,"yyyy-MM-dd HH:mm");
                if(currentTime.compareTo(updateTime)>=0){

                        startTime = convert2ValidAWSDateTime(formatter.format(currentTime.getTime()));
                        endTime = convert2ValidAWSDateTime(formatter.format(currentTime.getTime()));

                        data.add(Integer.parseInt(heartRate.value));
                        String tempID = UUID.randomUUID().toString();
                        processing = true;
                        createFitnessDataMutation(tempID,member[0],startTime,endTime,"heartRate",new ArrayList<Integer>(data));
                        while(processing){
                            try {
                                Thread.sleep(1000L);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        data.clear();

                }

            }
        }
        MokoSupport.getInstance().setHeartRates(null);
        //Update Battery
    }

    public void createFitnessDataMutation(String id,String member,String startTime,String endTime,String type,List<Integer> data){
        CreateFitnessDataInput createFitnessDataInput = CreateFitnessDataInput.builder()
                .id(id)
                .memberId(member)
                .startDate(startTime)
                .endDate(endTime)
                .type(type)
                .value(data)
                .build();
        mAWSAppSyncClient.mutate(CreateFitnessDataMutation.builder().input(createFitnessDataInput).build())
                .enqueue(createFitnessDataCallback);
    }

    private GraphQLCall.Callback<CreateFitnessDataMutation.Data> createFitnessDataCallback = new GraphQLCall.Callback<CreateFitnessDataMutation.Data>() {
        @Override
        public void onResponse(@Nonnull Response<CreateFitnessDataMutation.Data> response) {

            Log.i("Results", "Created New Fitness Data");
            String member = "\nMember: " + response.data().createFitnessData().memberId();
            String start = "\nstartDate: " + response.data().createFitnessData().startDate();
            String end = "\nendDate: " + response.data().createFitnessData().endDate();
            String type = "\nType: " + response.data().createFitnessData().type();
            String data ="\nData: " + response.data().createFitnessData().value().get(0);
            Log.d("New Fitness Data Attributes:",member+start+end+type+data);
            processing = false;
            //finish();



        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e("Error", e.toString());
            processing = false;
        }
    };

    public void runUpdateMutation(String member,String timestamp,String type,String device){
        UpdateLastUpdatedTimeInput updateLastUpdatedTimeInput = UpdateLastUpdatedTimeInput.builder().
                id(lastUpdatedId).
                member(member).
                timestamp(timestamp).
                battery(MokoSupport.getInstance().getBatteryQuantity()).
                //type(type).
                device(device).
                build();
        Log.d("Mutation",updateLastUpdatedTimeInput.toString());

        mAWSAppSyncClient.mutate(UpdateLastUpdatedTimeMutation.builder().input(updateLastUpdatedTimeInput).build())
                .enqueue(mutationCallback);
        //while(processing){

        //}
        Log.d("Mutation","Mutation finished");
    }

    private GraphQLCall.Callback<UpdateLastUpdatedTimeMutation.Data> mutationCallback = new GraphQLCall.Callback<UpdateLastUpdatedTimeMutation.Data>() {
        @Override
        public void onResponse(@Nonnull Response<UpdateLastUpdatedTimeMutation.Data> response) {

                    Log.i("Results", "Updated Last Update Time");
                    processing = false;
                    //finish();



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
        //TableStringFilterInput typeFilter = TableStringFilterInput.builder().eq(type).build();
        TableLastUpdatedTimeFilterInput lastUpdatedFilter = TableLastUpdatedTimeFilterInput.builder()
                .member(memberFilter)
                //.type(typeFilter)
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

            //if(response.data().listLastUpdatedTimes()!=null){
            if(response.data().listLastUpdatedTimes().items().size()!=0) {

                Log.i("Results", "\n\nID:"+response.data().listLastUpdatedTimes().items().get(0).id() + "\n" + response.data().listLastUpdatedTimes().items().get(0).timestamp()+"\n\n\n");
                lastUpdatedString = response.data().listLastUpdatedTimes().items().get(0).timestamp();
                lastUpdatedId = response.data().listLastUpdatedTimes().items().get(0).id();
                deviceMAC = response.data().listLastUpdatedTimes().items().get(0).device();
                processing = false;
            }
            else {
                Log.d("Results","Null result");
                processing = false;
            }
            //finish();
        }




        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e("ERROR", e.toString());
            processing = false;
        }
    };

    public void runCreateUpdateMutation(String member,String timestamp,String type,String device){
        CreateLastUpdatedTimeInput createLastUpdatedTimeInput = CreateLastUpdatedTimeInput.builder().
                member(member).
                timestamp(timestamp).
                type(type).
                device(device).
                build();
        Log.d("Mutation",createLastUpdatedTimeInput.toString());

        mAWSAppSyncClient.mutate(CreateLastUpdatedTimeMutation.builder().input(createLastUpdatedTimeInput).build())
                .enqueue(createMutationCallback);

        Log.d("Mutation","Mutation finished");
    }

    private GraphQLCall.Callback<CreateLastUpdatedTimeMutation.Data> createMutationCallback = new GraphQLCall.Callback<CreateLastUpdatedTimeMutation.Data>() {
        @Override
        public void onResponse(@Nonnull Response<CreateLastUpdatedTimeMutation.Data> response) {


                    Log.i("Results", "Updated Last Update Time");
                    processing = false;
                    //finish();



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
        processing = false;
    }


}
