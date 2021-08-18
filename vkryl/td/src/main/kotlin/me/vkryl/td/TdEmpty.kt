@file:JvmName("Td")
@file:JvmMultifileClass

package me.vkryl.td

import org.drinkless.td.libcore.telegram.TdApi.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@ExperimentalContracts
fun FormattedText?.isEmpty (): Boolean {
  contract {
    returns(false) implies (this@isEmpty != null)
  }
  return this == null || this.text.isNullOrEmpty()
}

@ExperimentalContracts
fun RichText?.isEmpty (): Boolean {
  contract {
    returns(false) implies (this@isEmpty != null)
  }
  if (this == null) return true
  return when (this.constructor) {
    RichTextPlain.CONSTRUCTOR -> (this as RichTextPlain).text.isNullOrEmpty()
    RichTextIcon.CONSTRUCTOR, RichTextAnchor.CONSTRUCTOR -> false
    RichTextBold.CONSTRUCTOR -> (this as RichTextBold).text.isEmpty()
    RichTextAnchorLink.CONSTRUCTOR -> (this as RichTextAnchorLink).text.isEmpty()
    RichTextEmailAddress.CONSTRUCTOR -> (this as RichTextEmailAddress).text.isEmpty()
    RichTextFixed.CONSTRUCTOR -> (this as RichTextFixed).text.isEmpty()
    RichTextItalic.CONSTRUCTOR -> (this as RichTextItalic).text.isEmpty()
    RichTextMarked.CONSTRUCTOR -> (this as RichTextMarked).text.isEmpty()
    RichTextPhoneNumber.CONSTRUCTOR -> (this as RichTextPhoneNumber).text.isEmpty()
    RichTextReference.CONSTRUCTOR -> (this as RichTextReference).text.isEmpty()
    RichTextStrikethrough.CONSTRUCTOR -> (this as RichTextStrikethrough).text.isEmpty()
    RichTextSubscript.CONSTRUCTOR -> (this as RichTextSubscript).text.isEmpty()
    RichTextSuperscript.CONSTRUCTOR -> (this as RichTextSuperscript).text.isEmpty()
    RichTextUnderline.CONSTRUCTOR -> (this as RichTextUnderline).text.isEmpty()
    RichTextUrl.CONSTRUCTOR -> (this as RichTextUrl).text.isEmpty()
    RichTexts.CONSTRUCTOR -> {
      for (childText in (this as RichTexts).texts) {
        if (!childText.isEmpty()) return false
      }
      return true
    }
    else -> error(this.toString())
  }
}

@ExperimentalContracts
fun DraftMessage?.isEmpty (): Boolean {
  contract {
    returns(false) implies (this@isEmpty != null)
  }
  return (this == null) || (
    this.replyToMessageId == 0L &&
    (this.inputMessageText as InputMessageText).text.isEmpty()
  )
}

@ExperimentalContracts
fun StatisticalValue?.isEmpty (): Boolean {
  contract {
    returns(false) implies (this@isEmpty != null)
  }
  return (this == null) || (this.value == this.previousValue && this.value == 0.0)
}