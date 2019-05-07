package com.mapbox.mapboxsdk.plugins.offline.offline;

import android.support.annotation.NonNull;
import com.mapbox.mapboxsdk.plugins.offline.model.GroupedOfflineDownloadOptions;

import java.util.HashSet;
import java.util.Set;

public class GroupedOfflineDownloadChangeDispatcher implements GroupedOfflineDownloadChangeListener {

  @NonNull
  private final Set<GroupedOfflineDownloadChangeListener> changeListeners = new HashSet<>();

  void addListener(GroupedOfflineDownloadChangeListener groupedOfflineDownloadChangeListener) {
    changeListeners.add(groupedOfflineDownloadChangeListener);
  }

  void removeListener(GroupedOfflineDownloadChangeListener groupedOfflineDownloadChangeListener) {
    changeListeners.remove(groupedOfflineDownloadChangeListener);
  }

  @Override
  public void onSuccess(GroupedOfflineDownloadOptions groupedOfflineDownload) {
    for (GroupedOfflineDownloadChangeListener listener : changeListeners) {
      listener.onSuccess(groupedOfflineDownload);
    }
  }

  @Override
  public void onCancel(GroupedOfflineDownloadOptions groupedOfflineDownload) {
    for (GroupedOfflineDownloadChangeListener listener : changeListeners) {
      listener.onCancel(groupedOfflineDownload);
    }
  }

  @Override
  public void onError(GroupedOfflineDownloadOptions groupedOfflineDownload, String error, String message) {
    for (GroupedOfflineDownloadChangeListener listener : changeListeners) {
      listener.onError(groupedOfflineDownload, error, message);
    }
  }

  @Override
  public void onProgress(GroupedOfflineDownloadOptions groupedOfflineDownload) {
    for (GroupedOfflineDownloadChangeListener listener : changeListeners) {
      listener.onProgress(groupedOfflineDownload);
    }
  }
}
