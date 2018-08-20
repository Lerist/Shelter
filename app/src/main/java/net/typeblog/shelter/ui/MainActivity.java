package net.typeblog.shelter.ui;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import net.typeblog.shelter.R;
import net.typeblog.shelter.ShelterApplication;
import net.typeblog.shelter.receivers.ShelterDeviceAdminReceiver;
import net.typeblog.shelter.services.IShelterService;
import net.typeblog.shelter.util.LocalStorageManager;
import net.typeblog.shelter.util.Utility;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PROVISION_PROFILE = 1;
    private static final int REQUEST_START_SERVICE_IN_WORK_PROFILE = 2;

    private LocalStorageManager mStorage = null;
    private DevicePolicyManager mPolicyManager = null;

    // Two services running in main / work profile
    private IShelterService mServiceMain = null;
    private IShelterService mServiceWork = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mStorage = LocalStorageManager.getInstance();
        mPolicyManager = getSystemService(DevicePolicyManager.class);

        if (mPolicyManager.isProfileOwnerApp(getPackageName())) {
            // We are now in our own profile
            // We should never start the main activity here.
            android.util.Log.d("MainActivity", "started in user profile. stopping.");
            finish();
        } else {
            if (!mStorage.getBoolean(LocalStorageManager.PREF_IS_DEVICE_ADMIN)) {
                // TODO: Navigate to the Device Admin settings page
                throw new IllegalStateException("TODO");
            }

            if (!mStorage.getBoolean(LocalStorageManager.PREF_HAS_SETUP)) {
                setupProfile();
            } else {
                // Initialize the app
                initializeApp();
            }
        }

    }

    private void setupProfile() {
        // Check if provisioning is allowed
        if (!mPolicyManager.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)) {
            Toast.makeText(this,
                    getString(R.string.msg_device_unsupported), Toast.LENGTH_LONG).show();
            finish();
        }

        // Start provisioning
        ComponentName admin = new ComponentName(getApplicationContext(), ShelterDeviceAdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
        intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);
        intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin);
        startActivityForResult(intent, REQUEST_PROVISION_PROFILE);
    }

    private void initializeApp() {
        // Bind to the service provided by this app in main user
        ((ShelterApplication) getApplication()).bindShelterService(new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceMain = IShelterService.Stub.asInterface(service);
                bindWorkService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // dummy
            }
        });
    }

    private void bindWorkService() {
        // Bind to the ShelterService in work profile
        Intent intent = new Intent("net.typeblog.shelter.action.START_SERVICE");
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        Utility.transferIntentToProfile(this, intent);
        startActivityForResult(intent, REQUEST_START_SERVICE_IN_WORK_PROFILE);
    }

    private void buildView() {
        // Finally we can build the view
        // TODO: Actually implement this method
        try {
            android.util.Log.d("MainActivity", "Main profile app count: " + mServiceMain.getApps().size());
            android.util.Log.d("MainActivity", "Work profile app count: " + mServiceWork.getApps().size());
        } catch (Exception e) {

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            // For the work instance, we just kill it entirely
            // We don't need it to be awake to do anything useful
            mServiceWork.stopShelterService(true);
        } catch (RemoteException e) {
            // We are stopping anyway
        }

        try {
            mServiceMain.stopShelterService(false);
        } catch (RemoteException e) {
            // We are stopping anyway
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PROVISION_PROFILE && resultCode == RESULT_OK) {
            // Provisioning finished.
            // Set the HAS_SETUP flag
            mStorage.setBoolean(LocalStorageManager.PREF_HAS_SETUP, true);

            // Initialize the app just as if the activity was started.
            // TODO: Should not initialize here. It is possible that the process is not finished yet.
            //initializeApp();
        } else if (requestCode == REQUEST_START_SERVICE_IN_WORK_PROFILE && resultCode == RESULT_OK) {
            // TODO: Set the service in work profile as foreground to keep it alive
            Bundle extra = data.getBundleExtra("extra");
            IBinder binder = extra.getBinder("service");
            mServiceWork = IShelterService.Stub.asInterface(binder);
            buildView();
        }
    }
}