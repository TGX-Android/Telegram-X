package org.thunderdog.challegram.telegram;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageLoader;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Letters;

import java.util.ArrayList;

public class TdlibAppShortcutManager {
    private final Tdlib tdlib;

    private TdApi.User[] users;
    private Bitmap[] userAvatars;
    private int avatarLoadIndex;

    public TdlibAppShortcutManager (Tdlib tdlib) {
        this.tdlib = tdlib;
    }

    public void requestAndPublish () {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return;
        }

        if (users != null && userAvatars != null) {
            avatarLoadIndex = 0;
            loadAvatar(users[avatarLoadIndex]);
        } else {
            tdlib.client().send(new TdApi.GetTopChats(new TdApi.TopChatCategoryUsers(), 5), r -> {
                if (r.getConstructor() != TdApi.Chats.CONSTRUCTOR) {
                    return;
                }

                TdApi.Chats chats = (TdApi.Chats) r;

                users = new TdApi.User[chats.chatIds.length];
                userAvatars = new Bitmap[chats.chatIds.length];
                avatarLoadIndex = 0;

                for (int i = 0; i < chats.chatIds.length; i++) {
                    users[i] = tdlib.chatUser(chats.chatIds[i]);
                }

                loadAvatar(users[avatarLoadIndex]);
            });
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private void loadAvatar (TdApi.User currentUser) {
        Runnable onEnd = () -> {
            if (++avatarLoadIndex == users.length) {
                onLoadFinished();
            } else {
                loadAvatar(users[avatarLoadIndex]);
            }
        };

        if (currentUser.profilePhoto != null) {
            ImageLoader.instance().loadFile(new ImageFile(tdlib, currentUser.profilePhoto.small, null), (success, bitmap) -> {
                if (success) {
                    userAvatars[avatarLoadIndex] = bitmap;
                }

                onEnd.run();
            });
        } else {
            onEnd.run();
        }
    }

    private static String getSettingKey (long accountId) {
        return "integration_" + accountId;
    }

    private static String getShortcutId (long accountId, int index) {
        return accountId + "-" + index;
    }

    // Account ID is not needed to be hidden because it is just an index
    // However, user IDs should be hidden inside the DB
    private static void saveIdMap (TdApi.User[] users, long accountId) {
        long[] usersMap = new long[users.length];
        for (int i = 0; i < users.length; i++) {
            usersMap[i] = users[i].id;
        }
        Settings.instance().pmc().putLongArray(getSettingKey(accountId), usersMap);
    }

    public static long toTelegramId (long accountId, long id) {
        try {
            long[] idMap = Settings.instance().pmc().getLongArray(getSettingKey(accountId));
            if (idMap != null) {
                return idMap[(int) id];
            } else {
                return 0;
            }
        } catch (Exception e) {
            Log.e(e);
            return 0;
        }
    }

    public void onUserLogout (int tdlibAccountId) {
        long[] idMap = Settings.instance().pmc().getLongArray(getSettingKey(tdlibAccountId));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || idMap == null) return;

        android.content.pm.ShortcutManager sm = (android.content.pm.ShortcutManager) UI.getAppContext().getSystemService(Context.SHORTCUT_SERVICE);
        ArrayList<String> shortcutList = new ArrayList<>();

        for (int i = 0; i < idMap.length; i++) {
            shortcutList.add(getShortcutId(tdlibAccountId, i));
        }

        sm.disableShortcuts(shortcutList, Lang.getString(R.string.ShortcutDisabledByLogout));
    }

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private void onLoadFinished () {
        saveIdMap(users, tdlib.accountId());
        ArrayList<android.content.pm.ShortcutInfo> shortcuts = new ArrayList<>();

        for (int i = 0; i < users.length; i++) {
            TdApi.User user = users[i];

            android.content.pm.ShortcutInfo.Builder shortcutInfo = new android.content.pm.ShortcutInfo.Builder(UI.getAppContext(), getShortcutId(tdlib.accountId(), i));

            Intent intent = new Intent(UI.getAppContext(), MainActivity.class);
            intent.setAction(Intents.ACTION_OPEN_CHAT + "." + tdlib.accountId() + "." + i + "." + Math.random());
            intent.setPackage(BuildConfig.APPLICATION_ID);
            intent.putExtra("account_id", tdlib.accountId());
            intent.putExtra("chat_id", (long) i);
            intent.putExtra("secure", true);

            shortcutInfo.setShortLabel(user.firstName);
            shortcutInfo.setLongLabel(tdlib.cache().userName(user.id));
            shortcutInfo.setIntent(intent);
            shortcutInfo.setActivity(new ComponentName(UI.getAppContext(), MainActivity.class));

            Bitmap bitmap = userAvatars[i] != null ? circleCropBitmap(userAvatars[i]) : renderLetterBitmap(user);
            shortcutInfo.setIcon(android.graphics.drawable.Icon.createWithBitmap(bitmap));

            shortcuts.add(shortcutInfo.build());
        }

        android.content.pm.ShortcutManager sm = (android.content.pm.ShortcutManager) UI.getAppContext().getSystemService(Context.SHORTCUT_SERVICE);
        sm.setDynamicShortcuts(shortcuts);
    }

    private Bitmap renderLetterBitmap (TdApi.User user) {
        int size = 150;

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float radius = size / 2f;

        Letters letters = TD.getLetters(user);
        int avatarColorId = TD.getAvatarColorId(user.id, tdlib.myUserId());
        float lettersSize = 25f;
        float lettersWidth = Paints.measureLetters(letters, lettersSize);

        canvas.drawCircle(radius, radius, radius, Paints.fillingPaint(Theme.getColor(avatarColorId)));
        Paints.drawLetters(canvas, letters, radius - lettersWidth / 2, radius + Screen.dp(8f), lettersSize);

        return bitmap;
    }

    // The following part is wisely taken from Coil-KT

    private Bitmap circleCropBitmap (Bitmap adaptiveIconBitmap) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        int minSize = Math.min(adaptiveIconBitmap.getWidth(), adaptiveIconBitmap.getHeight());
        float radius = minSize / 2f;

        Bitmap newBitmap = Bitmap.createBitmap(adaptiveIconBitmap.getWidth(), adaptiveIconBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        canvas.drawCircle(radius, radius, radius, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(adaptiveIconBitmap, radius - adaptiveIconBitmap.getWidth() / 2f, radius - adaptiveIconBitmap.getHeight() / 2f, paint);

        return newBitmap;
    }
}
