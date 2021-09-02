package org.thunderdog.challegram.telegram;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.ThumbnailUtils;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.graphics.drawable.IconCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.json.JSONArray;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
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

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private void onLoadFinished () {
        saveIdMap(users, tdlib.accountId());
        ArrayList<android.content.pm.ShortcutInfo> shortcuts = new ArrayList<>();

        for (int i = 0; i < users.length; i++) {
            TdApi.User user = users[i];

            android.content.pm.ShortcutInfo.Builder shortcutInfo = new android.content.pm.ShortcutInfo.Builder(UI.getAppContext(), tdlib.accountId() + "-" + i);

            Intent intent = new Intent(UI.getAppContext(), MainActivity.class);
            intent.setAction(Intents.ACTION_OPEN_CHAT + "." + tdlib.accountId() + "." + i + "." + Math.random());
            intent.setPackage(BuildConfig.APPLICATION_ID);
            intent.putExtra("account_id", tdlib.accountId());
            intent.putExtra("chat_id", (long) i);
            intent.putExtra("secure", true);

            String username = tdlib.cache().userName(user.id);
            shortcutInfo.setShortLabel(username);
            shortcutInfo.setLongLabel(username);
            shortcutInfo.setIntent(intent);
            shortcutInfo.setActivity(new ComponentName(UI.getAppContext(), MainActivity.class));

            Bitmap bitmap = userAvatars[i] != null ? createLegacyIconFromAdaptiveIcon(userAvatars[i]) : renderLetterBitmap(user);
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

    // The following part is wisely taken from AndroidX

    private static final float ADAPTIVE_ICON_INSET_FACTOR = 1 / 4f;
    private static final float DEFAULT_VIEW_PORT_SCALE = 1 / (1 + 2 * ADAPTIVE_ICON_INSET_FACTOR);
    private static final float ICON_DIAMETER_FACTOR = 176f / 192;

    private Bitmap createLegacyIconFromAdaptiveIcon (Bitmap adaptiveIconBitmap) {
        int size = (int) (DEFAULT_VIEW_PORT_SCALE * Math.min(adaptiveIconBitmap.getWidth(), adaptiveIconBitmap.getHeight()));

        Bitmap icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        float center = size * 0.5f;
        float radius = center * ICON_DIAMETER_FACTOR;

        paint.setColor(Color.BLACK);
        BitmapShader shader = new BitmapShader(adaptiveIconBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix shift = new Matrix();
        shift.setTranslate(-(adaptiveIconBitmap.getWidth() - size) / 2f, -(adaptiveIconBitmap.getHeight() - size) / 2f);
        shader.setLocalMatrix(shift);
        paint.setShader(shader);
        canvas.drawCircle(center, center, radius, paint);

        canvas.setBitmap(null);
        return icon;
    }
}
