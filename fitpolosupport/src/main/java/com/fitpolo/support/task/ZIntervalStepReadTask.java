package com.fitpolo.support.task;

import com.fitpolo.support.MokoConstants;
import com.fitpolo.support.MokoSupport;
import com.fitpolo.support.callback.MokoOrderTaskCallback;
import com.fitpolo.support.entity.OrderEnum;
import com.fitpolo.support.entity.OrderType;
import com.fitpolo.support.entity.Step;
import com.fitpolo.support.log.LogModule;
import com.fitpolo.support.utils.ComplexDataParse;
import com.fitpolo.support.utils.DigitalConver;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

public class ZIntervalStepReadTask extends OrderTask {
    private static final int ORDERDATA_LENGTH = 8;
    private static final int HEADER_STEP_COUNT = 0x05;
    private static final int HEADER_STEP = 0x06;

    private byte[] orderData;

    private int stepCount;
    private ArrayList<Step> steps;
    private HashMap<Integer,Boolean> stepsMap;

    private boolean isCountSuccess;
    private boolean isReceiveDetail;

    public ZIntervalStepReadTask(MokoOrderTaskCallback callback, Calendar lastSyncTime) {
        super(OrderType.STEP_CHARACTER, OrderEnum.Z_READ_RECENT_STEPS, callback, OrderTask.RESPONSE_TYPE_WRITE_NO_RESPONSE);

        lastSyncTime.add(Calendar.MINUTE,-30);
        int year = lastSyncTime.get(Calendar.YEAR) - 2000;
        int month = lastSyncTime.get(Calendar.MONTH) + 1;
        int day = lastSyncTime.get(Calendar.DAY_OF_MONTH);

        int hour = lastSyncTime.get(Calendar.HOUR_OF_DAY);
        int minute = lastSyncTime.get(Calendar.MINUTE);

        orderData = new byte[ORDERDATA_LENGTH];
        orderData[0] = (byte) MokoConstants.HEADER_SETP_SEND;
        orderData[1] = (byte) order.getOrderHeader();
        orderData[2] = (byte) 0x05;
        orderData[3] = (byte) year;
        orderData[4] = (byte) month;
        orderData[5] = (byte) day;
        orderData[6] = (byte) hour;
        orderData[7] = (byte) minute;
        //LogModule.i("Made it here");
    }

    @Override
    public void parseValue(byte[] value) {
        int header = DigitalConver.byte2Int(value[1]);
        int data_length = DigitalConver.byte2Int(value[2]);
        LogModule.i(order.getOrderName() + "成功");
        switch (header) {
            case HEADER_STEP_COUNT:
                if (data_length != 2) {
                    return;
                }
                isCountSuccess = true;
                byte[] count = new byte[2];
                System.arraycopy(value, 3, count, 0, 2);
                stepCount = DigitalConver.byteArr2Int(count);
                MokoSupport.getInstance().setStepCount(stepCount);
                LogModule.i("There are " + stepCount + " step data");
                MokoSupport.getInstance().initStepsListInterval();
                steps = MokoSupport.getInstance().getSteps();
                stepsMap = MokoSupport.getInstance().getStepsMap();

                if (stepCount != 0) {
                    // 拿到条数后再启动超时任务
                    stepsMap.put(stepCount, false);
                    MokoSupport.getInstance().setStepsMap(stepsMap);
                    MokoSupport.getInstance().timeoutHandler(this);
                }
                break;

            case HEADER_STEP:
                if (data_length != 14) {
                    return;
                }
                if (value.length <= 2 || orderStatus == OrderTask.ORDER_STATUS_SUCCESS)
                    return;
                if (stepCount > 0) {
                    if (steps == null) {
                        steps = new ArrayList<>();
                    }
                    if (stepsMap ==null){
                        stepsMap = new HashMap<>();
                    }
                    stepsMap.put(stepCount,true);
                    ComplexDataParse.parseStep(value, steps);
                    stepCount--;
                    MokoSupport.getInstance().setSteps(steps);
                    MokoSupport.getInstance().setStepCount(stepCount);
                    if (stepCount > 0) {
                        LogModule.i("还有" + stepCount + "条记步数据未同步"); //stepCount step items left to synch
                        stepsMap.put(stepCount, false);
                        MokoSupport.getInstance().setStepsMap(stepsMap);
                        orderTimeoutHandler(stepCount);
                        return;
                    }
                }
                break;
            default:
                return;
        }
        if (stepCount != 0) {
            return;
        }
        // 对心率数据做判重处理，避免时间重复造成的数据问题
        //fix time repetition errors
        HashMap<String, Step> removeRepeatMap = new HashMap<>();
        for (Step step : steps) {
            removeRepeatMap.put(step.time, step);
        }
        if (steps.size() != removeRepeatMap.size()) {
            steps.clear();
            steps.addAll(removeRepeatMap.values());
        }
        MokoSupport.getInstance().setStepCount(stepCount);
        MokoSupport.getInstance().setSteps(steps);
        MokoSupport.getInstance().setStepprocessing(false);
        orderStatus = OrderTask.ORDER_STATUS_SUCCESS;
        MokoSupport.getInstance().pollTask();
        callback.onOrderResult(response);
        MokoSupport.getInstance().executeTask(callback);
    }

    @Override
    public byte[] assemble() {
        return orderData;
    }

    @Override
    public boolean timeoutPreTask() {
        if (!isCountSuccess) {
            LogModule.i(order.getOrderName() + "个数超时"); //number of timeouts
        } else {
            return false;
        }
        return super.timeoutPreTask();
    }

    private void orderTimeoutHandler(final int stepCount) {
        MokoSupport.getInstance().getHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (stepsMap != null
                        && !stepsMap.isEmpty()
                        && stepsMap.get(stepCount) != null
                        && !stepsMap.get(stepCount)) {
                    orderStatus = OrderTask.ORDER_STATUS_SUCCESS;
                    LogModule.i("获取心率第" + stepCount + "个数据超时");
                    MokoSupport.getInstance().pollTask();
                    callback.onOrderTimeout(response);
                    MokoSupport.getInstance().executeTask(callback);
                }
            }
        }, delayTime);
    }
}
