package com.mapbox.mapboxsdk.plugins.offline.offline;

import com.mapbox.mapboxsdk.plugins.offline.model.GroupedOfflineDownloadOptions;
import com.mapbox.mapboxsdk.plugins.offline.model.OfflineDownloadOptions;

public interface GroupedOfflineDownloadChangeListener {

  void onProgress(GroupedOfflineDownloadOptions groupedOfflineDownload);

  void onPartialSuccess(GroupedOfflineDownloadOptions groupedOfflineDownload, OfflineDownloadOptions offlineDownload);

  void onSuccess(GroupedOfflineDownloadOptions groupedOfflineDownload);

  void onCancel(GroupedOfflineDownloadOptions groupedOfflineDownload);

  void onError(GroupedOfflineDownloadOptions groupedOfflineDownload, String error, String message);

}
