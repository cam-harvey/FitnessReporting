package com.routinify.fitnessreporting;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.amplify.generated.graphql.CreateLastUpdatedTimeMutation;
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
import com.fitpolo.support.entity.BleDevice;
import com.fitpolo.support.task.ZReadStepTask;
import com.fitpolo.support.task.ZReadVersionTask;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import javax.annotation.Nonnull;

import butterknife.ButterKnife;
import type.CreateLastUpdatedTimeInput;
import type.TableLastUpdatedTimeFilterInput;
import type.TableStringFilterInput;
import type.UpdateLastUpdatedTimeInput;

public class SyncActivity extends AppCompatActivity{

    private AWSAppSyncClient mAWSAppSyncClient;
    private BleDevice mDevice;
    private boolean mIsUpgrade;
    private MokoService mService;
    private boolean processing;
    private String lastUpdatedString;
    private String lastUpdatedId;

    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_query_results);

        mAWSAppSyncClient = AWSAppSyncClient.builder()
                .context(getApplicationContext())
                .awsConfiguration(new AWSConfiguration(getApplicationContext()))
                .build();

        ButterKnife.bind(this);
        mDevice = (BleDevice) getIntent().getSerializableExtra("device");
        bindService(new Intent(this, MokoService.class), mServiceConnection, BIND_AUTO_CREATE);
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String action = intent.getAction();
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                    switch (blueState) {
                        case BluetoothAdapter.STATE_TURNING_OFF:
                        case BluetoothAdapter.STATE_OFF:
                            SyncActivity.this.finish();
                            break;
                    }
                }
                if (MokoConstants.ACTION_CONN_STATUS_DISCONNECTED.equals(action)) {
                    abortBroadcast();
                    if (!mIsUpgrade) {
                        Toast.makeText(SyncActivity.this, "Connect failed", Toast.LENGTH_SHORT).show();
                        SyncActivity.this.finish();
                    }
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
            filter.addAction(MokoConstants.ACTION_DISCOVER_TIMEOUT);
            filter.addAction(MokoConstants.ACTION_ORDER_RESULT);
            filter.addAction(MokoConstants.ACTION_ORDER_TIMEOUT);
            filter.addAction(MokoConstants.ACTION_ORDER_FINISH);
            filter.addAction(MokoConstants.ACTION_CURRENT_DATA);
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.setPriority(200);
            registerReceiver(mReceiver, filter);
            // first
            MokoSupport.getInstance().sendOrder(new ZReadVersionTask(mService));
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
        unbindService(mServiceConnection);
    }

    public void sync(View view){
        Spinner dataTypeSpinner = findViewById(R.id.spinner);
        String dataType = dataTypeSpinner.getSelectedItem().toString();
        TextView memberId = findViewById(R.id.memberText);
        String memberIdText = memberId.getText().toString();

        //Runs the query to find the last update time for a fitness data type
        processing = true;
        runQuery(memberIdText,dataType);

        while(processing){

        }
        /*
        Setting up current time to pass to the update
         */
        Calendar currentCal = Calendar.getInstance();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        String currentTimeString = formatter.format(currentCal.getTime());
        currentTimeString = currentTimeString.substring(0, 23);
        currentTimeString = currentTimeString.concat("Z");
        Log.d("DateTime:", currentTimeString);
        if(lastUpdatedString!=null) {
            runUpdateMutation(memberIdText, currentTimeString, dataType,mDevice.address);
        }
        else{
            runCreateUpdateMutation(memberIdText,currentTimeString,dataType,mDevice.address);
        }

        Calendar calendar;
        if(lastUpdatedString!=null) {
            calendar = Utils.strDate2Calendar(lastUpdatedString, AppConstants.PATTERN_YYYY_MM_DD_HH_MM);
        }
        else{
            calendar = Utils.strDate2Calendar("2000-01-01'T'00:00:00.000'Z'", AppConstants.PATTERN_YYYY_MM_DD_HH_MM);
        }
        MokoSupport.getInstance().sendOrder(new ZReadStepTask(mService, calendar));
    }

    public void runUpdateMutation(String member,String timestamp,String type,String device){
        UpdateLastUpdatedTimeInput updateLastUpdatedTimeInput = UpdateLastUpdatedTimeInput.builder().
                id(lastUpdatedId).
                member(member).
                timestamp(timestamp).
                type(type).
                device(device).
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

            if(response.data().listLastUpdatedTimes()!=null)
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

    public void runCreateUpdateMutation(String member,String timestamp,String type,String device){
        CreateLastUpdatedTimeInput createLastUpdatedTimeInput = CreateLastUpdatedTimeInput.builder().
                member(member).
                timestamp(timestamp).
                type(type).
                device(device).
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
}
