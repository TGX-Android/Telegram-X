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
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageLoader;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class TdlibAppShortcutManager {
    private final Tdlib tdlib;

    private long[] chatIds;
    private Bitmap[] userAvatars;
    private int avatarLoadIndex;

    public TdlibAppShortcutManager (Tdlib tdlib) {
        this.tdlib = tdlib;
    }

    public void requestAndPublish () {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return;
        }

        if (chatIds != null && userAvatars != null) {
            tdlib.runOnTdlibThread(() -> {
                avatarLoadIndex = 0;
                loadAvatar(chatIds[avatarLoadIndex]);
            });
        } else {
            tdlib.client().send(new TdApi.GetTopChats(new TdApi.TopChatCategoryUsers(), 5), result -> {
                if (result.getConstructor() != TdApi.Chats.CONSTRUCTOR) {
                    return;
                }

                TdApi.Chats chats = (TdApi.Chats) result;

                this.chatIds = ((TdApi.Chats) result).chatIds;
                this.userAvatars = new Bitmap[chats.chatIds.length];
                this.avatarLoadIndex = 0;

                loadAvatar(this.chatIds[avatarLoadIndex]);
            });
        }
    }

    public void clear () {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return;
        }
        android.content.pm.ShortcutManager sm = (android.content.pm.ShortcutManager) UI.getAppContext().getSystemService(Context.SHORTCUT_SERVICE);
        sm.removeAllDynamicShortcuts();
    }

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private void loadAvatar (long currentChatId) {
        Runnable onEnd = () -> {
            if (++avatarLoadIndex == chatIds.length) {
                onLoadFinished();
            } else {
                loadAvatar(chatIds[avatarLoadIndex]);
            }
        };

        TdApi.ChatPhotoInfo photo = tdlib.chatPhoto(currentChatId);
        if (photo != null) {
            ImageFile file = new ImageFile(tdlib, photo.small, null);
            file.setSize(150);
            ImageLoader.instance().loadFile(file, (success, bitmap) -> {
                if (success) {
                    userAvatars[avatarLoadIndex] = circleCropBitmap(bitmap);
                }

                onEnd.run();
            });
        } else {
            onEnd.run();
        }
    }

    private static String getShortcutId (long accountId, long localChatId) {
        return accountId + "_chat" + localChatId;
    }

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private void onLoadFinished () {
        ArrayList<android.content.pm.ShortcutInfo> shortcuts = new ArrayList<>();

        int index = 0;
        for (long chatId : chatIds) {
            long localChatId = tdlib.settings().getLocalChatId(chatId);

            android.content.pm.ShortcutInfo.Builder shortcutInfo = new android.content.pm.ShortcutInfo.Builder(UI.getAppContext(), getShortcutId(tdlib.accountId(), localChatId));

            Intent intent = Intents.valueOfLocalChatId(tdlib.id(), localChatId, 0, true);
            shortcutInfo.setShortLabel(tdlib.chatTitleShort(chatId));
            shortcutInfo.setLongLabel(tdlib.chatTitle(chatId));
            shortcutInfo.setIntent(intent);
            shortcutInfo.setActivity(new ComponentName(UI.getAppContext(), MainActivity.class));

            Set<String> categories = new HashSet<>();
            categories.add(android.content.pm.ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION);

            Bitmap bitmap;
            if (userAvatars[index] != null) {
                bitmap = userAvatars[index];
            } else {
                final int size = 150;
                AvatarPlaceholder placeholder = tdlib.chatPlaceholder(chatId, tdlib.chat(chatId), true, size / 2f, null);
                bitmap = renderLetterBitmap(placeholder, size);
            }
            shortcutInfo.setIcon(android.graphics.drawable.Icon.createWithBitmap(bitmap));
            shortcutInfo.setCategories(categories);
            shortcuts.add(shortcutInfo.build());
            index++;
        }

        android.content.pm.ShortcutManager sm = (android.content.pm.ShortcutManager) UI.getAppContext().getSystemService(Context.SHORTCUT_SERVICE);
        sm.setDynamicShortcuts(shortcuts);
    }

    private static Bitmap renderLetterBitmap (AvatarPlaceholder placeholder, int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float radius = size / 2f;
        placeholder.draw(canvas, radius, radius);
        return bitmap;
    }

    // The following part is wisely taken from Coil-KT

    private static Bitmap circleCropBitmap (Bitmap adaptiveIconBitmap) {
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
