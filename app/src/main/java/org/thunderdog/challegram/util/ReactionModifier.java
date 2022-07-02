package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.reactions.ReactionDrawable;
import org.thunderdog.challegram.component.reactions.ReactionsManager;
import org.thunderdog.challegram.component.reactions.TGReaction;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Screen;

public class ReactionModifier implements DrawModifier  {

   private ReactionDrawable reactionDrawable;
   private TGReaction tgReaction;

   public ReactionModifier(Tdlib tdlib, String reaction) {
      ReactionsManager reactionsManager = ReactionsManager.instance(tdlib);
      TdApi.Reaction tdReaction = reactionsManager.getReaction(reaction);
      if (tdReaction != null) {
         tgReaction = new TGReaction(tdlib, reactionsManager);
         tgReaction.setReaction(tdReaction);
      }
   }

   @Override
   public void beforeDraw(View view, Canvas c) {
      DrawModifier.super.beforeDraw(view, c);
      if (reactionDrawable == null && tgReaction != null) {
         ReactionDrawable.ReactionInfo reactionInfo = new ReactionDrawable.ReactionInfo(tgReaction, view);
         reactionDrawable = new ReactionDrawable(reactionInfo);
      }
   }

   @Override
   public void afterDraw(View view, Canvas c) {
      DrawModifier.super.afterDraw(view, c);
      if (reactionDrawable != null) {
         float x = view.getMeasuredWidth() - reactionDrawable.getMinimumWidth() - Screen.dp(18);
         float y = (view.getMeasuredHeight() - reactionDrawable.getMinimumHeight()) / 2f;
         Drawables.draw(c, reactionDrawable, x, y, null);
      }
   }
}
