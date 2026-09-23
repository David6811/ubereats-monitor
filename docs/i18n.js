/*
 * Every word the page says, in both languages.
 *
 * The markup carries the Chinese and a key: data-t on a text node, data-tp on
 * a placeholder. Strings built in script ask t(key) for theirs. The choice is
 * kept in this browser, and defaults to the browser's own language.
 */
(function(){
  "use strict";

  var WORDS = {
  t1: ["接单规则", "Offer Rules"],
  t2: ["在哪改都行，保存后手机几秒内就用上", "Change it anywhere; the phone has it seconds after you save"],
  t3: ["账号：…", "Account: …"],
  t4: ["保存", "Save"],
  t5: ["选区", "Areas"],
  t6: ["不接的店", "Shops"],
  t7: ["规则参数", "Numbers"],
  t8: ["单子", "Jobs"],
  t9: ["账号", "Account"],
  t10: ["去哪些区", "Where you go"],
  t11: ["回到选中的区", "Fit to the set"],
  t12: ["全部取消", "Clear all"],
  t13: ["量范围", "Measure"],
  t14: ["画不接单框", "Draw a no-go box"],
  t15: ["去", "going"],
  t16: ["不去（只剩轮廓）", "not going (outline only)"],
  t17: ["不接单框", "no-go box"],
  t18: ["取餐店名字里含有这些字就不接。照派单卡上写的打，回车加进去；\n      只打最有辨识度的那一段，比如「Walrus BBQ」，不用抄整行。地点上的不接，用选区页的不接单框画。", "A pickup whose name contains any of these is refused. Type it as the offer card writes it and press Enter; just the distinctive part, like \"Walrus BBQ\", not the whole line. To refuse a place rather than a shop, draw a no-go box on the Areas page."],
  t19: ["店名，回车拉黑", "Shop name, Enter to refuse"],
  t20: ["改完记得点右上角「保存」。", "Press Save, top right, when you are done."],
  t21: ["远区门槛 $", "Far threshold $"],
  t22: ["派单金额超过这个数，就改用「远区」那一套选区来判。", "An offer over this is judged against the far set instead."],
  t23: ["在手机上改的", "Set on the phone"],
  t24: ["回中心模式、近中心模式的公里数和分钟数，油钱、时间倍数、远区每小时最低 —— 这些在手机 App 的「设置」页改，跑单时顺手就能调。", "Homeward and near-centre kilometres and minutes, fuel per km, time factor, far floor per hour — those live on the phone's Settings page, where they can be changed mid-shift."],
  t25: ["手机看到的每一张派单，几秒内到这里。接了的、没接的、为什么。", "Every offer the phone saw, here within seconds: taken, refused, and why."],
  t26: ["全部", "All"],
  t27: ["接了", "Taken"],
  t28: ["建议接", "Worth it"],
  t29: ["建议不接", "Not worth it"],
  t30: ["刷新", "Refresh"],
  t31: ["时间", "Time"],
  t32: ["钱", "Pay"],
  t33: ["取餐", "Pick up"],
  t34: ["送到", "Drop off"],
  t35: ["判断", "Verdict"],
  t36: ["理由", "Reason"],
  t37: ["邮箱", "Email"],
  t38: ["规则最后保存", "Rules last saved"],
  t39: ["手机 App 用同一个邮箱登录，规则就是同一份。", "Sign in on the phone with the same email and the rules are the same copy."],
  t40: ["退出登录", "Sign out"],
  t41: ["登录", "Sign in"],
  t42: ["密码", "Password"],
  t43: ["新增方案", "New set"],
  t44: ["名字", "Name"],
  t45: ["比如：晚上 / 周末早上", "e.g. Evenings / Weekend mornings"],
  t46: ["中心地址", "Centre address"],
  t47: ["打店名或街名，从下面选", "Type a shop or street, then pick one"],
  t48: ["还没定位。", "Nothing located yet."],
  t49: ["先选上中心", "Start with areas within"],
  t50: ["公里内的区", "km of the centre"],
  t51: ["建好", "Create"],
  t52: ["取消", "Cancel"],
  t53: ["圆心 —— 也可以直接在地图上点", "Centre — or just click the map"],
  t54: ["输入地址", "Type an address"],
  t55: ["半径", "Radius"],
  t56: ["公里", "km"],
  t57: ["这台电脑的位置", "This computer's position"],
  t58: ["收起", "Close"],
  t59: ["设中心", "Set the centre"],
  t60: ["选一个位置 —— 或直接在地图上点", "Pick a place — or click the map"],
  t61: ["或者输入地址", "Or type an address"],
  t62: ["设为中心", "Make it the centre"],
  t63: ["清掉", "Clear"],
  t64: ["手机上的单", "Jobs on the phone"],
  t65: ["关掉", "Close"],
  t66: ["知道了", "Got it"],
  t67: ["规则存在云端你的账号下，手机登录同一个账号就会自动同步。跑单时手机上只会看到「可以接单 / 不要接单」和一句原因。", "Your rules live in the cloud under your account; sign in on the phone with the same account and they follow. On shift the phone shows only take it or leave it, and one line of reason."],
  j1: ["回到选中的区", "Fit to the set"],
  j2: ["全部取消", "Clear all"],
  j3: ["量范围", "Measure"],
  j4: ["画不接单框", "Draw a no-go box"],
  j5: ["拖出一个框…（再点取消）", "Drag a box… (click again to cancel)"],
  j6: ["收起", "Close"],
  j7: ["改中心", "Change centre"],
  j8: ["收起中心", "Close centre"],
  j9: ["改名", "Rename"],
  j10: ["复制", "Copy"],
  j11: ["删除", "Delete"],
  j12: ["+ 新增", "+ New"],
  j13: ["已保存", "Saved"],
  j14: ["保存中…", "Saving…"],
  j15: ["保存失败", "Save failed"],
  j16: ["没保存", "Not saved"],
  j17: ["没保存成功", "Nothing was saved"],
  j18: ["已保存到云端", "Saved to the cloud"],
  j19: ["手机登录着的话，几秒内就按这套规则判单", "If the phone is signed in, it judges by these within seconds"],
  j20: ["云端没收下，规则一个字都没写出去。", "The cloud refused it; not a word was written."],
  j21: ["这个页面过期了", "This page is out of date"],
  j22: ["出错", "Error"],
  j23: ["一个区都没选", "No areas chosen"],
  j24: ["一个区都没选，等于哪都不去", "No areas chosen, which means going nowhere"],
  j25: ["正在定位…", "Locating…"],
  j26: ["定在 ", "Located at "],
  j27: ["圆心已定在 ", "Circle centred on "],
  j28: ["已用这台电脑的位置", "Used this computer's position"],
  j29: ["浏览器没给出位置 —— 允许定位后再试", "The browser gave no position — allow location and try again"],
  j30: ["账号：", "Account: "],
  j31: ["账号：没登录", "Account: not signed in"],
  j32: ["邮箱或密码不对", "Wrong email or password"],
  j33: ["读不到云端：", "Cannot read the cloud: "],
  j34: ["先起个名字", "Give it a name first"],
  j35: ["已经有同名的了", "There is already one with that name"],
  j36: ["先在地址栏回车定位中心", "Pick a centre from the address box first"],
  j37: ["还没设", "not set"],
  j38: ["已设", "set"],
  j39: ["没设中心", "no centre"],
  j40: ["中心 ", "centre "],
  j41: ["中心清掉了", "Centre cleared"],
  j42: ["还没选位置。", "No place picked yet."],
  j43: ["选中了：", "Picked: "],
  j44: [" —— 按下面的「设为中心」定下来", " — press Make it the centre below"],
  j45: ["按「设为中心」就定在这里", "Press Make it the centre to fix it here"],
  j46: ["画圈圆心", "Circle centre"],
  j47: ["自己输入地址…", "Type an address…"],
  j48: ["输入街名和郊区，回车定位", "Type a street and suburb, Enter to locate"],
  j49: ["按住鼠标在地图上拖，框里的地方不接单也不送", "Drag on the map; nothing inside the box is picked up or delivered"],
  j50: ["给这个框起个名字", "Name this box"],
  j51: ["不接单区 ", "No-go "],
  j52: ["框太小了，没加", "That box is too small, not added"],
  j53: ["删了一个不接单框，记得保存", "A no-go box is gone; remember to save"],
  j54: ["（点一下删掉）", "(click to remove)"],
  j55: ["点一下移除", "Click to remove"],
  j56: ["手机上现在没有单", "No jobs on the phone right now"],
  j57: ["留言：", "Note: "],
  j58: ["同单再送：", "Same offer also goes to: "],
  j59: ["已复制", "Copied"],
  j60: ["复制不了", "Could not copy"],
  j61: ["取货", "Pick up"],
  j62: ["地图", "Map"],
  j63: ["远区门槛改成 $", "Far threshold is now $"],
  j64: ["，记得保存", "; remember to save"],
  j65: ["超过门槛的单用这一组", "Offers over the threshold use this set"],
  j66: ["正在改，点一下回到方案", "Editing; click to go back to the set"],
  j67: ["还没保存过", "never saved"],
  j68: ["默认", "Default"],
  j69: ["选区方案", "Sets"],
  j70: ["市中心不接的店", "CBD shops refused"],
  j71: ["手动拉黑的店", "Shops refused by hand"],
  j72: ["去的区", "Areas going"],
  j73: ["可以", "OK"],

  // Sentences with a number or a name in them: %s in order, filled by f().
  f1: ["（取…）", "(fetching…)"],
  f2: ["（%s 单）", "(%s jobs)"],
  f3: ["（去 %s 个）", "(%s going)"],
  f4: ["（%s 条）", "(%s shops)"],
  f5: ["删掉「%s」？", "Delete \"%s\"?"],
  f6: ["加了「%s」，记得保存", "Added \"%s\"; remember to save"],
  f7: ["「%s」的中心", "Centre of \"%s\""],
  f8: ["「%s」的中心：%s", "Centre of \"%s\": %s"],
  f9: ["在改「%s」，%s 个区", "Editing \"%s\", %s areas"],
  f10: ["%s 个区", "%s areas"],
  f11: ["复制成「%s」，在列表里", "Copied as \"%s\"; it is in the list"],
  f12: ["删除方案「%s」？", "Delete the set \"%s\"?"],
  f13: ["远区 >$%s", "Far >$%s"],
  f14: ["「%s」建好了：中心 %s，%s 个区，在列表里", "\"%s\" created: centre %s, %s areas; it is in the list"],
  f15: ["已切到「%s」，%s 个区", "Switched to \"%s\", %s areas"],
  f16: ["中心", "centre"],
  f17: ["%s（%s）", "%s (%s)"],
  f18: ["「%s」拉黑了，记得保存", "\"%s\" refused; remember to save"],
  f19: ["%s 单，点地址在 Google 地图打开", "%s jobs; click an address to open Google Maps"],
  f20: ["（在改远区）", " (editing the far set)"],
  f21: ["%s 个", "%s"],
  f22: ["%s 家", "%s"],
  f23: ["先选一个位置", "Pick a place first"],
  f24: ["「%s」的中心设好了", "Centre set for \"%s\""],
  };

  var lang = "zh";
  try { lang = localStorage.getItem("lang") || ((navigator.language || "").indexOf("zh") === 0 ? "zh" : "en"); } catch (e) {}

  function t(key){
    var pair = WORDS[key];
    if (!pair) return key;
    return lang === "en" ? pair[1] : pair[0];
  }

  /** t(), with each %s replaced by the arguments in order. */
  function f(key){
    var rest = Array.prototype.slice.call(arguments, 1), at = 0;
    return t(key).replace(/%s/g, function(){ return String(rest[at++]); });
  }

  function applyLang(){
    document.documentElement.lang = lang === "en" ? "en" : "zh-CN";
    document.title = t("t1");
    document.querySelectorAll("[data-t]").forEach(function(node){ node.textContent = t(node.dataset.t); });
    document.querySelectorAll("[data-tp]").forEach(function(node){ node.placeholder = t(node.dataset.tp); });
  }

  function setLang(next){
    lang = next;
    try { localStorage.setItem("lang", next); } catch (e) {}
    // Everything drawn by script carries its own words, so the page is built
    // again rather than patched in place.
    location.reload();
  }

  window.I18N = { t: t, f: f, applyLang: applyLang, setLang: setLang, current: function(){ return lang; } };
})();
