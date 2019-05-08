package com.mapbox.mapboxsdk.plugins.offline.offline;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.util.LongSparseArray;
import com.mapbox.mapboxsdk.offline.*;
import com.mapbox.mapboxsdk.plugins.offline.R;
import com.mapbox.mapboxsdk.plugins.offline.model.GroupedOfflineDownloadOptions;
import com.mapbox.mapboxsdk.plugins.offline.model.NotificationOptions;
import com.mapbox.mapboxsdk.plugins.offline.model.OfflineDownloadOptions;
import com.mapbox.mapboxsdk.plugins.offline.utils.NotificationUtils;
import com.mapbox.mapboxsdk.snapshotter.MapSnapshot;
import com.mapbox.mapboxsdk.snapshotter.MapSnapshotter;
import timber.log.Timber;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.mapbox.mapboxsdk.plugins.offline.offline.OfflineConstants.KEY_BUNDLE;
import static com.mapbox.mapboxsdk.plugins.offline.utils.NotificationUtils.setupNotificationChannel;

/**
 * Internal usage only, use this service indirectly by using methods found in
 * {@link OfflinePlugin}. When an offline download is initiated
 * for the first time using this plugin, this service is created, captures the {@code StartCommand}
 * and collects the {@link OfflineDownloadOptions} instance which holds all the download metadata
 * needed to perform the download.
 * <p>
 * If another offline download is initiated through the
 * {@link OfflinePlugin} while another download is currently in
 * process, this service will add it to the {@link OfflineManager} queue for downloading,
 * downstream, this will execute the region downloads asynchronously (although writing to the same
 * file). Once all downloads have been completed, this service is stopped and destroyed.
 *
 * @since 0.1.0
 */
public class OfflineDownloadService extends Service {

  private static final int GROUPED_DOWNLOAD_ID = (int) UUID.randomUUID().getMostSignificantBits();

  private MapSnapshotter mapSnapshotter;
  NotificationManagerCompat notificationManager;
  NotificationCompat.Builder notificationBuilder;
  OfflineDownloadStateReceiver broadcastReceiver;

  // map offline regions to requests, ids are received with onStartCommand, these match serviceId
  // in OfflineDownloadOptions
  final LongSparseArray<OfflineRegion> regionLongSparseArray = new LongSparseArray<>();

  //private boolean isGroupedDownloadActive = false;
  private NotificationCompat.Builder groupedNotificationBuilder;
  //private int groupedDownloadCount = 0;
  private GroupedOfflineDownloadOptions currentGroupedDownloadOptions = null;
  private OfflineRegion currentGroupedDownloadOfflineRegion = null;

  final List<OfflineDownloadOptions> remainingGroupedDownloadList = new ArrayList<>();

  @Override
  public void onCreate() {
    super.onCreate();
    Timber.v("Service onCreate method called.");
    // Setup notification manager and channel
    notificationManager = NotificationManagerCompat.from(this);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setupNotificationChannel();
    }

    // Register the broadcast receiver needed for updating APIs in the OfflinePlugin class.
    broadcastReceiver = new OfflineDownloadStateReceiver();
    IntentFilter filter = new IntentFilter(OfflineConstants.ACTION_OFFLINE);
    getApplicationContext().registerReceiver(broadcastReceiver, filter);
  }

  // TODO documentation
  /**
   * Called each time a new download is initiated. First it acquires the
   * {@link OfflineDownloadOptions} or {@link GroupedOfflineDownloadOptions} from the intent and if found,
   * the process of downloading the offline region or grouped download carries on to the
   * {@link #onResolveCommand(String, Parcelable)}.
   * If the {@link OfflineDownloadOptions} fails to be found inside the intent, the service is
   * stopped (only if no other downloads are currently running) and throws a
   * {@link NullPointerException}.
   * <p>
   * {@inheritDoc}
   *
   * @since 0.1.0
   */
  @Override
  public int onStartCommand(final Intent intent, int flags, final int startId) {
    Timber.v("onStartCommand called.");
    if (intent != null) {
      final Parcelable parcelableOptions = intent.getParcelableExtra(KEY_BUNDLE);
      if (parcelableOptions != null) {
        onResolveCommand(intent.getAction(), parcelableOptions);
      } else {
        stopSelf(startId);
        throw new NullPointerException("A DownloadOptions instance must be passed into the service to"
          + " begin downloading.");
      }
    }
    return START_STICKY;
  }

  /**
   * Several actions can take place inside this service including starting and canceling a specific
   * region download. First, it is determined what action to take by using the {@code intentAction}
   * parameter. This action is finally passed in to the correct map offline methods.
   *
   * @param intentAction string holding the task that should be performed on the specific
   *                     download.
   * @param options      the download model {@link OfflineDownloadOptions} which defines the
   *                     region and other metadata needed to download the correct region.
   *                     This can also be a grouped download {@link GroupedOfflineDownloadOptions}.
   * @since 0.1.0
   */
  private void onResolveCommand(String intentAction, Parcelable options) {
    if (OfflineConstants.ACTION_START_DOWNLOAD.equals(intentAction)) {
      if (options instanceof OfflineDownloadOptions) {
        createDownload((OfflineDownloadOptions) options);
      } else if (options instanceof GroupedOfflineDownloadOptions) {
        initGroupedDownload((GroupedOfflineDownloadOptions) options);
      }
    } else if (OfflineConstants.ACTION_CANCEL_DOWNLOAD.equals(intentAction)) {
      if (options instanceof OfflineDownloadOptions) {
        cancelDownload((OfflineDownloadOptions) options);
      } else if (options instanceof GroupedOfflineDownloadOptions) {
        cancelGroupedDownload((GroupedOfflineDownloadOptions) options);
      }
    }
  }

  private void createDownload(final OfflineDownloadOptions offlineDownload) {
    final OfflineRegionDefinition definition = offlineDownload.definition();
    final byte[] metadata = offlineDownload.metadata();
    OfflineManager.getInstance(getApplicationContext())
      .createOfflineRegion(
        definition,
        metadata,
        new OfflineManager.CreateOfflineRegionCallback() {
          @Override
          public void onCreate(OfflineRegion offlineRegion) {
            OfflineDownloadOptions options
              = offlineDownload.toBuilder().uuid(offlineRegion.getID()).build();
            OfflineDownloadStateReceiver.dispatchStartBroadcast(getApplicationContext(), options);
            regionLongSparseArray.put(options.uuid(), offlineRegion);

            launchDownload(options, offlineRegion);
            showNotification(options);
          }

          @Override
          public void onError(String error) {
            OfflineDownloadStateReceiver.dispatchErrorBroadcast(getApplicationContext(), offlineDownload, error);
          }
        });
  }

  private synchronized void initGroupedDownload(@NonNull GroupedOfflineDownloadOptions groupedOfflineDownloadOptions) {
    if (groupedOfflineDownloadOptions.offlineDownloadOptionsList().isEmpty()) {
      final String error = "Grouped download list is empty!";
      OfflineDownloadStateReceiver.dispatchErrorBroadcast(getApplicationContext(), groupedOfflineDownloadOptions, error);
      return;
    }

    if (remainingGroupedDownloadList.isEmpty() && currentGroupedDownloadOptions == null) {
      currentGroupedDownloadOptions = groupedOfflineDownloadOptions;
      remainingGroupedDownloadList.addAll(groupedOfflineDownloadOptions.offlineDownloadOptionsList());
      showGroupedNotification(groupedOfflineDownloadOptions);
      launchGroupedDownload(remainingGroupedDownloadList.get(0));
    } else {
      final String error = "A grouped download is already active.";
      OfflineDownloadStateReceiver.dispatchErrorBroadcast(getApplicationContext(), groupedOfflineDownloadOptions, error);
    }
  }

  void showNotification(final OfflineDownloadOptions offlineDownload) {
    notificationBuilder = NotificationUtils.toNotificationBuilder(this,
      offlineDownload, OfflineDownloadStateReceiver.createNotificationIntent(
        getApplicationContext(), offlineDownload), offlineDownload.notificationOptions(),
      OfflineDownloadStateReceiver.createCancelIntent(getApplicationContext(), offlineDownload)
    );
    startForeground(offlineDownload.uuid().intValue(), notificationBuilder.build());

    if (offlineDownload.notificationOptions().requestMapSnapshot()) {
      // create map bitmap to show as notification icon
      createMapSnapshot(offlineDownload.definition(), new MapSnapshotter.SnapshotReadyCallback() {
        @Override
        public void onSnapshotReady(MapSnapshot snapshot) {
          final int regionId = offlineDownload.uuid().intValue();
          if (regionLongSparseArray.get(regionId) != null) {
            notificationBuilder.setLargeIcon(snapshot.getBitmap());
            notificationManager.notify(regionId, notificationBuilder.build());
          }
        }
      });
    }
  }

  private void showGroupedNotification(final GroupedOfflineDownloadOptions groupedOfflineDownloadOptions) {
    final NotificationOptions notificationOptions = groupedOfflineDownloadOptions.notificationOptions();
    final NotificationCompat.Action cancelAction = new NotificationCompat.Action(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ? 0 : R.drawable.ic_cancel,
      notificationOptions.cancelText(),
      PendingIntent.getService(this,
        GROUPED_DOWNLOAD_ID,
        OfflineDownloadStateReceiver.createCancelIntent(getApplicationContext(), groupedOfflineDownloadOptions),
        PendingIntent.FLAG_CANCEL_CURRENT));
    groupedNotificationBuilder = NotificationUtils.toNotificationBuilder(this,
      OfflineDownloadStateReceiver.createNotificationIntent(getApplicationContext(), groupedOfflineDownloadOptions), groupedOfflineDownloadOptions.notificationOptions(), cancelAction);

    startForeground(GROUPED_DOWNLOAD_ID, groupedNotificationBuilder.build());
  }

  private void createMapSnapshot(OfflineRegionDefinition definition,
                                 MapSnapshotter.SnapshotReadyCallback callback) {
    Resources resources = getResources();
    int height = (int) resources.getDimension(android.R.dimen.notification_large_icon_height);
    int width = (int) resources.getDimension(android.R.dimen.notification_large_icon_width);

    MapSnapshotter.Options options = new MapSnapshotter.Options(width, height);
    options.withStyle(definition.getStyleURL());
    options.withRegion(definition.getBounds());
    mapSnapshotter = new MapSnapshotter(this, options);
    mapSnapshotter.start(callback);
  }

  private void cancelDownload(final OfflineDownloadOptions offlineDownload) {
    int serviceId = offlineDownload.uuid().intValue();
    OfflineRegion offlineRegion = regionLongSparseArray.get(serviceId);
    if (offlineRegion != null) {
      offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE);
      offlineRegion.setObserver(null);
      offlineRegion.delete(new OfflineRegion.OfflineRegionDeleteCallback() {
        @Override
        public void onDelete() {
          // no-op
        }

        @Override
        public void onError(String error) {
          OfflineDownloadStateReceiver.dispatchErrorBroadcast(getApplicationContext(), offlineDownload, error);
        }
      });
    }
    OfflineDownloadStateReceiver.dispatchCancelBroadcast(getApplicationContext(), offlineDownload);
    removeOfflineRegion(serviceId);
  }

  private void cancelGroupedDownload(@NonNull GroupedOfflineDownloadOptions groupedOfflineDownloadOptions) {
    // TODO find a better way to cancel the current download, parameter is unused and not really required.
    if (currentGroupedDownloadOptions != null) {
      final GroupedOfflineDownloadOptions cancelledGroupedDownload = currentGroupedDownloadOptions;
      currentGroupedDownloadOfflineRegion.delete(new OfflineRegion.OfflineRegionDeleteCallback() {
        @Override
        public void onDelete() {
          // no-op
        }

        @Override
        public void onError(String error) {
          final String message = "Cannot delete region.";
          OfflineDownloadStateReceiver.dispatchErrorBroadcast(getApplicationContext(),
            cancelledGroupedDownload, error, message);
        }
      });
      OfflineDownloadStateReceiver.dispatchCancelBroadcast(getApplicationContext(), cancelledGroupedDownload);
      if (groupedNotificationBuilder != null) {
        notificationManager.cancel(GROUPED_DOWNLOAD_ID);
      }
      remainingGroupedDownloadList.clear();
      currentGroupedDownloadOptions = null;
      if (regionLongSparseArray.isEmpty()) {
        stopForeground(true);
      }
      stopSelf(GROUPED_DOWNLOAD_ID);
    }
  }

  private synchronized void removeOfflineRegion(int regionId) {
    if (notificationBuilder != null) {
      notificationManager.cancel(regionId);
    }
    regionLongSparseArray.remove(regionId);
    if (regionLongSparseArray.isEmpty()) {
      stopForeground(true);
    }
    stopSelf(regionId);
  }

  void launchDownload(final OfflineDownloadOptions offlineDownload, final OfflineRegion offlineRegion) {
    offlineRegion.setObserver(new OfflineRegion.OfflineRegionObserver() {
      @Override
      public void onStatusChanged(OfflineRegionStatus status) {
        if (status.isComplete()) {
          finishDownload(offlineDownload, offlineRegion);
          return;
        }
        progressDownload(offlineDownload, status);
      }

      @Override
      public void onError(OfflineRegionError error) {
        OfflineDownloadStateReceiver.dispatchErrorBroadcast(getApplicationContext(), offlineDownload,
          error.getReason(), error.getMessage());
        stopSelf(offlineDownload.uuid().intValue());
      }

      @Override
      public void mapboxTileCountLimitExceeded(long limit) {
        OfflineDownloadStateReceiver.dispatchErrorBroadcast(getApplicationContext(), offlineDownload,
          "Mapbox tile count limit exceeded:" + limit);
      }
    });

    // Change the region state
    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE);
  }

  synchronized void launchGroupedDownload(final OfflineDownloadOptions offlineDownload) {
    final OfflineRegionDefinition definition = offlineDownload.definition();
    final byte[] metadata = offlineDownload.metadata();
    OfflineManager.getInstance(getApplicationContext())
      .createOfflineRegion(
        definition,
        metadata,
        new OfflineManager.CreateOfflineRegionCallback() {
          @Override
          public void onCreate(final OfflineRegion offlineRegion) {
            currentGroupedDownloadOfflineRegion = offlineRegion;
            currentGroupedDownloadOfflineRegion.setObserver(new OfflineRegion.OfflineRegionObserver() {
              @Override
              public void onStatusChanged(OfflineRegionStatus status) {
                if (status.isComplete()) {
                  OfflineDownloadStateReceiver.dispatchPartialSuccessBroadcast(getApplicationContext(),
                    currentGroupedDownloadOptions, offlineDownload);
                  downloadNext();
                } else {
                  progressGroupedDownload(offlineDownload, status);
                }
              }

              @Override
              public void onError(OfflineRegionError error) {
                OfflineDownloadStateReceiver.dispatchErrorBroadcast(getApplicationContext(), currentGroupedDownloadOptions,
                  error.getReason(), error.getMessage());
              }

              @Override
              public void mapboxTileCountLimitExceeded(long limit) {
                OfflineDownloadStateReceiver.dispatchErrorBroadcast(getApplicationContext(), currentGroupedDownloadOptions,
                  "Mapbox tile count limit exceeded:" + limit);
              }
            });

            // Change the region state
            currentGroupedDownloadOfflineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE);
          }

          @Override
          public void onError(String error) {
            OfflineDownloadStateReceiver.dispatchErrorBroadcast(getApplicationContext(), currentGroupedDownloadOptions,
              error, "Cannot create offline region.");
          }
        });
  }

  private void downloadNext() {
    synchronized (remainingGroupedDownloadList) {
      if (remainingGroupedDownloadList.isEmpty()) {
        finishGroupedDownload();
        return;
      }
      remainingGroupedDownloadList.remove(0);
      if (remainingGroupedDownloadList.isEmpty()) {
        finishGroupedDownload();
      } else {
        final OfflineDownloadOptions nextDownload = remainingGroupedDownloadList.get(0);
        launchGroupedDownload(nextDownload);
      }
    }
  }

  /**
   * When a particular download has been completed, this method's called which handles removing the
   * notification and setting the download state.
   *
   * @param offlineRegion   the region which has finished being downloaded
   * @param offlineDownload the corresponding options used to define the offline region
   * @since 0.1.0
   */
  void finishDownload(OfflineDownloadOptions offlineDownload, OfflineRegion offlineRegion) {
    OfflineDownloadStateReceiver.dispatchSuccessBroadcast(this, offlineDownload);
    offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE);
    offlineRegion.setObserver(null);
    removeOfflineRegion(offlineDownload.uuid().intValue());
  }

  private void finishGroupedDownload() {
    if (groupedNotificationBuilder != null) {
      notificationManager.cancel(GROUPED_DOWNLOAD_ID);
    }
    OfflineDownloadStateReceiver.dispatchSuccessBroadcast(getApplicationContext(), currentGroupedDownloadOptions);
    currentGroupedDownloadOptions = null;
    stopForeground(true);
    stopSelf(GROUPED_DOWNLOAD_ID);
  }

  void progressDownload(OfflineDownloadOptions offlineDownload, OfflineRegionStatus status) {
    final int percentage = (int) getProgress(status);

    offlineDownload = offlineDownload.toBuilder().progress(percentage).build();

    if (percentage % 2 == 0 && regionLongSparseArray.get(offlineDownload.uuid().intValue()) != null) {
      OfflineDownloadStateReceiver.dispatchProgressChanged(this, offlineDownload, percentage);
      if (notificationBuilder != null) {
        notificationBuilder.setProgress(100, percentage, false);
        notificationManager.notify(offlineDownload.uuid().intValue(), notificationBuilder.build());
      }
    }
  }

  private void progressGroupedDownload(OfflineDownloadOptions offlineDownload, OfflineRegionStatus status) {
    if (currentGroupedDownloadOptions != null) {
      final int downloadCount = currentGroupedDownloadOptions.offlineDownloadOptionsList().size();
      final int finishedDownloads = downloadCount - remainingGroupedDownloadList.size();
      final int percentageSingleDownload = (int) getProgress(status);
      final double percentageGroupedDownload = (percentageSingleDownload + finishedDownloads * 100.0) / downloadCount;
      final int percentageGroupedDownloadInt = (int) percentageGroupedDownload;

      if (percentageGroupedDownloadInt % 2 == 0) {
        offlineDownload = offlineDownload.toBuilder().progress(percentageSingleDownload).build();
        currentGroupedDownloadOptions = currentGroupedDownloadOptions.toBuilder()
          .currentOfflineDownload(offlineDownload)
          .progress(percentageGroupedDownload)
          .build();
        OfflineDownloadStateReceiver.dispatchProgressChanged(getApplicationContext(), currentGroupedDownloadOptions);

        if (groupedNotificationBuilder != null) {
          groupedNotificationBuilder.setProgress(100, percentageGroupedDownloadInt, false);
          groupedNotificationBuilder.setContentTitle(offlineDownload.regionName());
          notificationManager.notify(GROUPED_DOWNLOAD_ID, groupedNotificationBuilder.build());
        }
      }
    }
  }

  private double getProgress(OfflineRegionStatus status) {
    return status.getRequiredResourceCount() >= 0 ? (100.0 * status.getCompletedResourceCount() / status.getRequiredResourceCount()) : 0.0;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (mapSnapshotter != null) {
      mapSnapshotter.cancel();
    }
    if (broadcastReceiver != null) {
      getApplicationContext().unregisterReceiver(broadcastReceiver);
    }
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    // don't provide binding
    return null;
  }
}
