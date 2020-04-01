package com.connect.mssql;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.appcompat.app.AppCompatActivity;

import com.connect.mssql.Adapter.ScanListAdapter;
import com.connect.mssql.Model.AllProducts;
import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKManager.EMDKListener;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.barcode.BarcodeManager;
import com.symbol.emdk.barcode.ScanDataCollection;
import com.symbol.emdk.barcode.ScanDataCollection.ScanData;
import com.symbol.emdk.barcode.Scanner;
import com.symbol.emdk.barcode.Scanner.DataListener;
import com.symbol.emdk.barcode.Scanner.StatusListener;
import com.symbol.emdk.barcode.Scanner.TriggerType;
import com.symbol.emdk.barcode.ScannerConfig;
import com.symbol.emdk.barcode.ScannerException;
import com.symbol.emdk.barcode.ScannerResults;
import com.symbol.emdk.barcode.StatusData;
import com.symbol.emdk.barcode.StatusData.ScannerStates;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static com.symbol.emdk.barcode.StatusData.ScannerStates.DISABLED;
import static com.symbol.emdk.barcode.StatusData.ScannerStates.ERROR;
import static com.symbol.emdk.barcode.StatusData.ScannerStates.SCANNING;

public class ScannerActivity extends AppCompatActivity implements EMDKListener, StatusListener, DataListener{

    Connection con;
    String username, password, databasename, ipaddress, portNumber, dbTableName;
    SharedPreferences pref;
    int j = 0;
    private Boolean isClicked = false;
    TextView product_date, barcode_QTY, sscc_info;
    ImageView backBtn;
    private ListView listView;
    ArrayList<String> ordLineNo = new ArrayList<>();
    ArrayList<List> selectedProducts = new ArrayList<>();
    ArrayList<List> ordLinePro = new ArrayList<>();
    private String CSM_NO = "";
    private String ART_NO = "";
    AllProducts allProducts = new AllProducts();
    //TODO; emdk parts

    private EMDKManager emdkManager = null;
    private BarcodeManager barcodeManager = null;
    private Scanner scanner = null;
    private int dataLength=  0;

    String scannedData = "";
    int len;
    String compPRODATE = "";
    String compBARQTY_1 = "";
    String compBARQTY_2 = "";
    String compSSCC = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        backBtn = findViewById(R.id.backBtn);
        listView = findViewById(R.id.ordLineNoList);
//        listView.setVisibility(View.GONE);
        product_date = findViewById(R.id.proDateDisp);
        barcode_QTY = findViewById(R.id.barcodeQtyDisp);
        sscc_info = findViewById(R.id.ssccDisp);



        this.overridePendingTransition(R.anim.animation_enter,
                R.anim.animation_leave);

        Intent extra = getIntent();
        CSM_NO = extra.getStringExtra("CSM_NO");
        ART_NO = extra.getStringExtra("ART_NO");

        getOldData();
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(ScannerActivity.this, ArtListActivity.class);
                intent.putExtra("CSM_NO", CSM_NO);

                finish();
            }
        });


        selectedProducts = allProducts.getAllproducts();

        for (int i = 0; i < selectedProducts.size(); i ++) {
            if (selectedProducts.get(i).get(1).equals(CSM_NO) && selectedProducts.get(i).get(4).equals(ART_NO)) {
                ordLineNo.add((String) selectedProducts.get(i).get(3));
            }
        }


        ScanListAdapter scanListAdapter = new ScanListAdapter(ScannerActivity.this, ordLineNo);
        listView.setAdapter(scanListAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id){
                for (int i = 0; i < selectedProducts.size(); i ++) {
                    if (selectedProducts.get(i).get(1).equals(CSM_NO) && selectedProducts.get(i).get(4).equals(ART_NO) && selectedProducts.get(i).get(3).equals(ordLineNo.get(position))) {
                        ordLinePro.add(selectedProducts.get(i));
                    }
                }
                for (int j = 0; j < ordLinePro.size(); j ++) {
                    if (ordLinePro.get(j).get(7).equals(barcode_QTY.getText().toString()) && ordLinePro.get(j).get(8).equals(product_date.getText().toString()) && ordLinePro.get(j).get(9).equals(sscc_info.getText().toString()))
                        Toast.makeText(ScannerActivity.this, "Already scanned!", Toast.LENGTH_SHORT).show();
                    else
                        updateToSQL(position);
                }

            }
        });


        EMDKResults results = EMDKManager.getEMDKManager(getApplicationContext(), this);

        // Check the return status of getEMDKManager() and update the status TextView accordingly.
        if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
            updateStatus("EMDKManager object request failed!");
            return;
        } else {
            updateStatus("EMDKManager object initialization is   in   progress.......");
        }

    }

    private void getOldData() {
        pref = getApplicationContext().getSharedPreferences("MyPref", 0);

        // retrieve data
        ipaddress = pref.getString("ipaddress", "");
        portNumber = pref.getString("portNo", "");
        databasename = pref.getString("databasename", "");
        dbTableName = pref.getString("tableName", "");
        username = pref.getString("username", "");
        password = pref.getString("password", "");
    }

    private void initBarcodeManager() {
        // Get the feature object such as BarcodeManager object for accessing the feature.
        barcodeManager =  (BarcodeManager)emdkManager.getInstance(EMDKManager.FEATURE_TYPE.BARCODE);
        // Add external scanner connection listener.
        if (barcodeManager == null) {
            Toast.makeText(this, "Barcode scanning is not supported.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initScanner() {
        if (scanner == null) {
            // Get default scanner defined on the device
            scanner = barcodeManager.getDevice(BarcodeManager.DeviceIdentifier.DEFAULT);
            if(scanner != null) {
                // Implement the DataListener interface and pass the pointer of this object to get the data callbacks.
                scanner.addDataListener(this);

                // Implement the StatusListener interface and pass the pointer of this object to get the status callbacks.
                scanner.addStatusListener(this);

                // Hard trigger. When this mode is set, the user has to manually
                // press the trigger on the device after issuing the read call.
                // NOTE: For devices without a hard trigger, use TriggerType.SOFT_ALWAYS.
                scanner.triggerType =  TriggerType.HARD;

                try{
                    // Enable the scanner
                    // NOTE: After calling enable(), wait for IDLE status before calling other scanner APIs
                    // such as setConfig() or read().
                    scanner.enable();

                } catch (ScannerException e) {
                    updateStatus(e.getMessage());
                    deInitScanner();
                }
            } else {
                updateStatus("Failed to   initialize the scanner device.");
            }
        }


    }

    private void deInitScanner() {
        if (scanner != null) {
            try {
                // Release the scanner
                scanner.release();
            } catch (Exception e)   {
                updateStatus(e.getMessage());
            }
            scanner = null;
        }
    }

    @Override
    public void onOpened(EMDKManager emdkManager) {

        // Get a reference to EMDKManager
        this.emdkManager =  emdkManager;

        // Get a  reference to the BarcodeManager feature object
        initBarcodeManager();

        // Initialize the scanner
        initScanner();


    }
    public void onData(ScanDataCollection scanDataCollection) {

        String dataStr = "";
        if ((scanDataCollection != null) &&   (scanDataCollection.getResult() == ScannerResults.SUCCESS)) {
            ArrayList<ScanData> scanData =  scanDataCollection.getScanData();
            // Iterate through scanned data and prepare the data.

//            ScanDataCollection.LabelType labelType = data.getLabelType();
            // Concatenate barcode data and label type
            String barcodeData = scanData.get(0).getData();
//                product_date.setText(barcodeData);
            dataStr =  barcodeData;

//            for (ScanData data :  scanData) {
//                // Get the scanned dataString barcodeData =  data.getData();
//                // Get the type of label being scanned
//
////                dataStr =  barcodeData + "  " +  labelType;
//            }
            // Updates EditText with scanned data and type of label on UI thread.
            updateData(dataStr);
        }
    }

    private void updateData(final String result) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                // Update the dataView EditText on UI thread with barcode data and its label type.
                if (dataLength++ >= 50) {
                    // Clear the cache after 50 scans
//                    dataView.getText().clear();
                    dataLength = 0;
                }
//                dataView.append(result + "\n"); // editText
//                Toast.makeText(ScannerActivity.this, "Scanned barcode is " + result, Toast.LENGTH_SHORT).show();
                handleBarcode(result);

            }
        });
    }

    private void updateToSQL(int position) {
        con = connectionclass(username, password, databasename, ipaddress + ":" + portNumber);

        if (con == null) {
            Log.i("connection status", "connection null");
            Toast.makeText(ScannerActivity.this, "connection is failed", Toast.LENGTH_SHORT).show();

        } else {
            Log.i("connection is", "successful");
//            Toast.makeText(ScannerActivity.this, "connection is successful", Toast.LENGTH_SHORT).show();
            Statement stmt = null;
//            int cnt = 0;


            try {

                String barcode = barcode_QTY.getText().toString();
                String proDate = product_date.getText().toString();
                String sscc = sscc_info.getText().toString();
                if (!barcode.equals("") && !proDate.equals("") && !sscc.equals("")){

                    stmt = con.createStatement();

                    String orderlineNo = ordLineNo.get(position);
                    String csm = CSM_NO;
                    String art = ART_NO;
                    String query1 = "UPDATE " + dbTableName + String.format(" SET BARCODE_QTY='%s' WHERE ORD_LINE_NO=%s AND CSM_NO=%s AND ART_NO=%s", barcode, orderlineNo, csm, art);
                    String query2 = "UPDATE " + dbTableName + String.format(" SET PROD_DATE='%s' WHERE ORD_LINE_NO=%s AND CSM_NO=%s AND ART_NO=%s", proDate, orderlineNo, csm, art);
                    String query3 = "UPDATE " + dbTableName + String.format(" SET SSCC='%s' WHERE ORD_LINE_NO=%s AND CSM_NO=%s AND ART_NO=%s", sscc, orderlineNo, csm, art);
                    stmt.executeUpdate(query1);
                    stmt.executeUpdate(query2);
                    stmt.executeUpdate(query3);

                    Toast.makeText(ScannerActivity.this, "Updating '" + dbTableName + "' has succeed!", Toast.LENGTH_SHORT).show();

                    barcode_QTY.setText("");
                    product_date.setText("");
                    sscc_info.setText("");

                } else {
                    Toast.makeText(ScannerActivity.this, "There are one or more empty fields. Please make sure if you scanned all of things.", Toast.LENGTH_LONG).show();
                }

                con.close();
            }
            catch (SQLException e) {
                Log.e("ERROR", e.getMessage());
            }
        }
    }

    //TODO; barcode scaning result compare process here
    private void handleBarcode(String result) {
        len = result.length();
        compPRODATE = result.substring(0,2);
        compBARQTY_1 = result.substring(0,3);
        compBARQTY_2 = result.substring(len-3);
        compSSCC = result.substring(0,2);

        if (compPRODATE.equals("91")) {
            product_date.setText(result.substring(2, len));
        }
        if (compBARQTY_1.equals("240") && compBARQTY_2.contains("30")) {
            barcode_QTY.setText(result.substring(len-1));
        }
        if (compSSCC.equals("00")) {
            sscc_info.setText(result.substring(2));
        }

    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
//        if (emdkManager != null) {
//            emdkManager.release();
//            emdkManager= null;
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (emdkManager != null) {
            emdkManager.release();
            emdkManager= null;
        }
    }

    @Override
    public void onClosed() {
        // The EMDK closed unexpectedly. Release all the resources.
        if (emdkManager != null) {
            emdkManager.release();
            emdkManager= null;
        }
        updateStatus("EMDK closed unexpectedly! Please close and restart the application.");
    }

    @Override
    public void onStatus(StatusData statusData) {
        // The status will be returned on multiple cases. Check the state and take the action.
// Get the current state of scanner in background
        ScannerStates state =  statusData.getState();
        String statusStr = "";
        switch (state){
        case IDLE:
        // Scanner is idle and ready to change configuration and submit read.
//        statusStr = statusData.getFriendlyName()+" is   enabled and idle...";
        // Change scanner configuration. This should be done while the scanner is in IDLE state.
        setConfig();
        try {
            // Starts an asynchronous Scan. The method will NOT turn ON the scanner beam,
            //but puts it in a  state in which the scanner can be turned on automatically or by pressing a hardware trigger.
            scanner.read();
        }
        catch (ScannerException e)   {
            updateStatus(e.getMessage());
        }
        break;
        case WAITING:
        // Scanner is waiting for trigger press to scan...
        statusStr = "Scanner is waiting for trigger press...";
        break;
        case SCANNING:
        // Scanning is in progress...
        statusStr = "Scanning...";
        break;
        case DISABLED:
        // Scanner is disabledstatusStr = statusData.getFriendlyName()+" is disabled.";
        break;
        case ERROR:
        // Error has occurred during scanning
        statusStr = "An error has occurred.";
        break;
        default:
        break;
    }
        // Updates TextView with scanner state on UI thread.
        updateStatus(statusStr);
    }

    private void updateStatus(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Update the status text view on UI thread with current scanner state
//                statusTextView.setText(""+  status);
//                Toast.makeText(ScannerActivity.this, "" + status, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setConfig() {
        if (scanner != null) {try {
            // Get scanner config
            ScannerConfig config = scanner.getConfig();
            // Enable haptic feedback
            if (config.isParamSupported("config.scanParams.decodeHapticFeedback")) {
                config.scanParams.decodeHapticFeedback = true;
            }
            // Set scanner config
            scanner.setConfig(config);
        } catch (ScannerException e)   {
            updateStatus(e.getMessage());
        }
        }
    }

    @SuppressLint("NewApi")
    public Connection connectionclass(String username, String password, String databasename, String ipaddress) {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        Connection connection = null;
        String ConnectionUrl;
        try {
//            Toast.makeText(MainActivity.this, "step_1",Toast.LENGTH_SHORT).show();
            Class.forName("net.sourceforge.jtds.jdbc.Driver");
            ConnectionUrl = "jdbc:jtds:sqlserver://" + ipaddress + ";" + "databaseName=" + databasename + ";user=" + username + ";password=" + password + ";";
            connection = DriverManager.getConnection(ConnectionUrl);
//            Toast.makeText(MainActivity.this, "step_2",Toast.LENGTH_SHORT).show();

        }
        catch (SQLException se) {
//            Toast.makeText(MainActivity.this, "step_3",Toast.LENGTH_SHORT).show();

            Log.e("SQL", se.getMessage());
        }
        catch (ClassNotFoundException e) {
//            Toast.makeText(MainActivity.this, "step_4",Toast.LENGTH_SHORT).show();

            Log.e("ClassNotFound", e.getMessage());
        }
        catch (Exception e) {
//            Toast.makeText(MainActivity.this, "step_5",Toast.LENGTH_SHORT).show();

            Log.e("Exception", e.getMessage());
        }
        return connection;
    }
}
