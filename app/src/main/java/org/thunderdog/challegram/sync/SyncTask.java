/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 24/03/2019
 */
package org.thunderdog.challegram.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class SyncTask extends Worker {
  public SyncTask (@NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
  }

  public static void cancel (int accountId) {
    if (accountId == TdlibAccount.NO_ID) {
      WorkManager.getInstance(UI.getAppContext()).cancelAllWorkByTag("sync");
    } else {
      WorkManager.getInstance(UI.getAppContext()).cancelUniqueWork("sync:" + accountId);
    }
  }

  public static void schedule (long pushId, int accountId) {
    final String uniqueWorkName = accountId != TdlibAccount.NO_ID ? "sync:all" : "sync:" + accountId;
    OneTimeWorkRequest.Builder b = new OneTimeWorkRequest.Builder(SyncTask.class);
    b.setConstraints(new Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build());
    b.setInputData(new Data.Builder().putLong("push_id", pushId).putInt("account_id", accountId).build());
    b.addTag(uniqueWorkName);
    if (accountId != TdlibAccount.NO_ID)
      b.addTag("sync:specific");
    b.addTag("sync");
    b.setBackoffCriteria(BackoffPolicy.LINEAR, OneTimeWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS);
    OneTimeWorkRequest request = b.build();

    if (accountId == TdlibAccount.NO_ID) {
      WorkManager.getInstance(UI.getAppContext()).cancelAllWorkByTag("sync:specific");
    } else {
      LiveData<List<WorkInfo>> liveData = WorkManager.getInstance(UI.getAppContext()).getWorkInfosForUniqueWorkLiveData("sync:all");
      List<WorkInfo> workInfos = liveData.getValue();
      if (workInfos != null) {
        for (WorkInfo work : workInfos) {
          switch (work.getState()) {
            case RUNNING:
            case ENQUEUED:
              return;
          }
        }
      }
    }
    TDLib.Tag.notifications(pushId, accountId, "Enqueueing SyncTask, because the task is still not completed");
    WorkManager.getInstance(UI.getAppContext()).enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.REPLACE, request);
  }

  @NonNull
  @Override
  public Result doWork () {
    Data data = getInputData();
    final long pushId = data.getLong("push_id", 0);
    final int accountId = data.getInt("account_id", TdlibAccount.NO_ID);
    if (TdlibManager.makeSync(getApplicationContext(), accountId, TdlibManager.SYNC_CAUSE_WORK_MANAGER, pushId, !TdlibManager.inUiThread(), 0)) {
      return Result.success();
    } else {
      return Result.retry();
    }
  }
}
