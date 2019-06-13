package com.routinify.fitnessreporting;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
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


import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;


import type.CreateLastUpdatedTimeInput;
import type.TableLastUpdatedTimeFilterInput;
import type.TableStringFilterInput;
import type.UpdateLastUpdatedTimeInput;

public class MainActivity extends AppCompatActivity {
    private AWSAppSyncClient mAWSAppSyncClient;
    private String lastUpdatedString;
    private String lastUpdatedId;
    private boolean processing;
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

        setContentView(R.layout.activity_main);
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
        setContentView(R.layout.activity_main);
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
            //Log.d("Mutation",response.data().updateLastUpdatedTime().toString());
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
        public void onResponse(@Nonnull Response<ListLastUpdatedTimesQuery.Data> response) {

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
            //Log.d("Mutation",response.data().updateLastUpdatedTime().toString());
        }

        @Override
        public void onFailure(@Nonnull ApolloException e) {
            Log.e("Error", e.toString());
            processing = false;
        }
    };
}
