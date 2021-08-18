## How Telegram X was created

To understand better how the project was created and evolved, let's go through a little bit of its history.

### 2014–2017

While it's possible to find couple small common utility classes and widgets that date back to 2014, **Telegram X** mainly started as **Challegram** app in Telegram's **Android Challenge**:
* [Step 1](https://vk.com/wall-55882680_72): 21 April 2015 — 20 May 2015;
* [Step 2](https://vk.com/wall-55882680_77): 17 July 2015 – 20 August 2015;
* [Step 3](https://vk.com/wall-55882680_80): 01 February 2016 – 08 March 2016 (final).

Since then and until now (well, at least until this document gets published within the **Telegram X** repository) there has been only one project creator, developer, maintainer and editor.

When the contest was held, it was important to achieve the best speed, performance, animations, stability, and, at the same time, implement all features required by the contest. For example, in the step 3 you had to implement 47 features/tasks in 36 days, which meant you had to complete at least 2 features per day without day-offs. **Challegram** managed to finish them all and took 1st place in steps 2 and 3.

After several months break, development on **Challegram** has continued later in November 2016. It received several major updates among the 2017, it's possible to find the change logs in the [Challegram channel](https://t.me/Challegram).

As TDLib evolved, major parts of **Challegram** had to be rebuilt internally several times too, based on those changes. Some notable moments:

1. At first, there were no nullable types in TDLib data objects and you had to check for object validity. For example, for `TdApi.File`, which is widely used type among many app components, you had to check that its identifier is not equal to `0`. However, with nullable types introduction, you had to check all places where `TdApi.File` (or other types in similar situation) is referenced, you check for `null` instead.
2. At first, there was a single `TG.getClientInstance()` class with a single TDLib instance. However, with multi-account feature, all places, where TDLib was used, had to be rebuilt and replaced with TDLib context (which is part of the app, not TDLib, and can be found in `Tdlib` class inside `org.thunderdog.challegram.telegram` package);
3. With new `Tdlib` context, it was important to add correct `Tdlib` references inside all `ViewController`s, so it would be possible to have screens from different accounts opened. To achieve this, all `ViewController` constructor signatures and the way they were created had to be completely changed. Alongside, all `TG.getClientInstance()` had to be changed with that `Tdlib` reference.

There were several reasons to rebuild major parts of the app for regular features as well, because initial version made in 2015 completely did not take them in mind. Just a few of them:

1. With cloud Translations Platform support (which is described later in this document), all places, where string resources were used, got to be rewritten. The way plurals work had to be completely changed. All existing translations had to be dropped;
2. Originally, app was not intended to be themed, thus, in most places colors were hardcoded. Many things had to be rebuilt: the way colors are used, the way they got updated. System that allowed subscribing to theme updates for a smooth transition between theme switch had to be added too and supported on all screens.

As a result, it's possible to find that some components made in completely different styles. Some components are well-designed, some are driven by contest nature, some are just heavily-optimized in a way to avoid frame drops caused by Dalvik runtime, which is almost gone by now, but was the most used runtime back in 2015.

### 2018–2020

On January 22nd 2018 **Challegram** changed its icon and name to **Telegram X**. It's possible to browse history in [Telegram X channel](https://t.me/tgx_android). There's a list of major updates:

##### 2018

* [March Update](https://telegra.ph/Telegram-X-03-26)
* [July Update](https://telegra.ph/Telegram-X-07-27)
* [October Update](https://telegra.ph/Telegram-X-10-14)
* [October Update #2](https://t.me/tgx_android/129)

##### 2019

* [April Update](https://telegra.ph/Telegram-X-04-25)

##### 2020

* [January Update](https://telegra.ph/Telegram-X-01-23-2)
* [February Update](https://telegra.ph/Telegram-X-02-29)
* [Spring Update](https://telegra.ph/Telegram-X-04-23)

These are just the updates that got a nice post. There were much more in between, that didn't get one, and dozens of [open beta](https://play.google.com/apps/testing/org.thunderdog.challegram) versions.

### Going open-source

**Telegram X**, until fall 2020, has never been open-sourced, though it was intended to be so. There are several reasons for that:

* **TDLib**, the main component used by **Telegram X**, responsible for networking, encryption, and caching, is [open source](https://github.com/tdlib/td). It is the most important component for security research;
* **Telegram X** source code consists of mostly UI-related features and stuff that are, in most cases, not as useful, as TDLib source code for security research;
* Without creating reproducible builds utilities first, it wouldn't be possible to verify that exactly the same version is running live on Google Play.

However, now it's open-source, and everyone is welcome to review the code for vulnerabilities, and participate in the new contest series.

### 2021: New contest series

-- TODO

### Looking forward

-- TODO

Following chapters of this document should help you to deep-dive inside the **Telegram X** development, understand how things work, and help you achieving the best results in the contest.