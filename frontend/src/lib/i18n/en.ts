import type { Format } from "@/lib/format";

// Values sent by the backend with a reason code (see backend Reason.java).
export type ReasonValues = Record<string, unknown>;
type ReasonText = (v: ReasonValues, f: Format) => string;

const n = (v: unknown) => (typeof v === "number" ? v : Number(v));
const flowWord = (v: unknown) => ({ ACCUMULATION: "collecting", NEUTRAL: "balanced", DISTRIBUTION: "selling" } as Record<string, string>)[String(v)] ?? String(v);

export const en = {
  meta: {
    title: "IDX Intelligence",
    description: "Indonesian Stock Exchange research: prices, broker activity and backtested signals",
  },
  footer: {
    createdBy: "Created by",
    contact: "Questions, ideas or found a bug? Get in touch:",
    email: "Email",
    linkedin: "LinkedIn",
    notAdvice: "Research tool for personal use — not investment advice.",
  },
  common: {
    appName: "IDX Intelligence",
    tagline: "IDX research tool — not investment advice.",
    demoNotice: "DEMO DATA — NOT LIVE MARKET DATA",
    loadingMarket: "Loading market data…",
    loadingStock: "Loading stock data…",
    backendDown: "Cannot load market data right now. The server may be waking up — try again in a minute.",
    source: "Source",
    generated: "Generated",
    endOfDayFor: (date: string) => `End-of-day data for ${date}`,
    endOfDayThrough: (date: string) => `End-of-day data through ${date}`,
    demoFigures: "Figures are invented for development and are not market prices.",
    estValueNote: "Traded value is estimated as last price × volume. Not investment advice.",
  },
  nav: {
    label: "Main menu",
    overview: "Overview",
    stocks: "Stocks",
    ihsg: "IHSG",
    radar: "Broker Radar",
    shortlist: "Worth a look",
    brokers: "Brokers",
    watchlist: "★ My list",
  },
  chartControls: {
    interval: "Candle size",
    range: "Range",
    intervals: { "15m": "15m", "1h": "1H", "4h": "4H", "1d": "1D", "1w": "1W", "1mo": "1Mo" },
    intradayNote: "Intraday candles from Yahoo Finance, possibly delayed; refreshed at most every 10 minutes.",
    adjustedNote: "Prices adjusted for stock splits and dividends.",
    brokerDailyOnly: "Broker panels are shown on daily (1D) candles.",
  },
  live: {
    badge: "LIVE",
    // Plain string (not a function): these labels are passed to a Client Component.
    updated: "as of {time} WIB",
    lots: "Volume",
    value: "Value",
    vwap: "VWAP",
    noTradeToday: "No trades yet today",
    closedToday: "Today's close · market closed",
    paused: "Live updates paused after 15 minutes to save the free data limit.",
    resume: "Resume",
    budgetUsed: "Today's live-price limit is used up; prices below are delayed.",
    unavailable: "Live price unavailable right now; prices below are delayed.",
    source: "Real-time from IDX running trade via IDX Edge; refreshes every minute while this page is open.",
  },
  radar: {
    title: "Broker Radar",
    intro:
      "Tracked stocks ranked by recent broker flow: persistence first, confirmed by price. It describes what happened, not what will happen; its measured record so far is under “Track record”.",
    window: "Look-back",
    windowLabel: (days: number) => `${days} days`,
    dataAsOf: (date: string) => `Broker data through ${date}`,
    tracked: (n: number) => `${n} tracked stocks`,
    addMore: "Add a stock with “Load broker data” on its page.",
    rank: "#",
    stock: "Stock",
    score: "Score",
    status: "Flow",
    streak: "Streak",
    streakUp: (days: number) => `▲ ${days}d buying`,
    streakDown: (days: number) => `▼ ${days}d selling`,
    streakNone: "—",
    leadBroker: "Lead broker",
    leadBrokerValue: (code: string, days: number) => `${code} · ${days}d`,
    concentration: "Top-5 conc.",
    foreign: "Foreign",
    costGap: "vs buyers' cost",
    change5d: "5D",
    why: "Why",
    reasonsCount: (n: number) => `${n} reasons`,
    empty: "No tracked stocks have broker data yet.",
    filteredTitle: "Filtered out",
    filteredHint: "Strong flow, but these are hard to trade sensibly right now.",
    screenerTitle: "IDX Edge screener picks — measured",
    screenerIntro:
      "Daily market-wide picks from IDX Edge's screener. Their win rates are the provider's claims; the outcomes below are what prices actually did after each pick (close to close).",
    claimedWinRate: (pct: string) => `Claimed win rate (average): ${pct}`,
    afterDays: (days: number) => `After ${days} day${days === 1 ? "" : "s"}`,
    picksMeasured: (n: number) => `${n} picks measured`,
    hitRate: "Went up",
    avgReturn: "Avg return",
    vsIhsg: "vs IHSG",
    notYet: "Not enough days yet",
    pickDate: "Date",
    bucket: "Group",
    claimed: "Claimed WR",
    entry: "Close",
    noPicks: "No picks saved yet. They're saved every weekday at 19:15 WIB.",
    views: { cards: "Ranking", streaks: "Streaks", table: "Table", record: "Track record" },
    record: {
      title: "How the Radar would have done",
      intro: (cost: string) =>
        `The Radar replayed on every past broker day, using only the data known that evening. Each "trade" buys at the next day's open and sells at the close 1, 3 or 5 trading days later, after ${cost} fees, and is compared with IHSG over the same days.`,
      twoKinds: (window: number) =>
        `Two different "days": the 5 / 10 / 20-day buttons set how much broker history the Radar looks at when it scores a stock (now: the last ${window} days). The +1 / +3 / +5 columns are how long each simulated trade is held after the pick.`,
      group: "Group",
      groups: {
        TOP_PICKS: (n: number) => `Top ${n} by score each day`,
        HIGH_SCORE: (n: number) => `Score ${n} or more`,
        ACCUMULATION: "Accumulation",
        NEUTRAL: "Neutral",
        DISTRIBUTION: "Distribution",
        ALL: "All tracked stocks (baseline)",
      },
      after: (days: number) => `+${days} days`,
      vsIhsg: "vs IHSG",
      rose: (pct: string) => `${pct} made money`,
      trades: (n: number) => `${n} trades`,
      period: (from: string, to: string, days: number, stocks: number) =>
        `${from} – ${to} · ${days} broker days · ${stocks} stocks`,
      tooLittle: (days: number) =>
        `Only ${days} broker days so far: far too few to tell luck from skill. Treat the Radar as unproven until there are several months.`,
      summary: (top: string, all: string) =>
        `So far, buying the daily top picks and holding them 5 trading days did ${top} vs IHSG; buying every tracked stock did ${all}.`,
      better: "That is better than the baseline — so far.",
      worse: "That is worse than the baseline: the score has not helped so far.",
      teaser: (top: string, days: number) => `Measured so far: top picks ${top} vs IHSG after 5 days (${days} days of history).`,
      teaserLink: "See the track record",
      inShortTitle: "In short",
      inShort: (n: number, top: string, all: string) =>
        `If you had bought the Radar's top ${n} stocks every day and sold them 5 trading days later, each trade would on average have done ${top} compared with IHSG. Buying any of the tracked stocks instead would have done ${all}.`,
      verdictBetter: "So far the Radar's picks did better than picking any tracked stock.",
      verdictWorse: "So far the Radar's picks did worse than picking any tracked stock: the ranking hasn't helped yet.",
      stepsTitle: "How this test works",
      steps: [
        "Every evening in the past, the Radar ranks the tracked stocks using only the broker data known that evening (nothing from later days).",
        "The test \"buys\" the picked stocks at the next morning's opening price.",
        "It \"sells\" them 1, 3 or 5 trading days later at the closing price and subtracts 0,4% for fees.",
        "It compares that result with what IHSG did over exactly the same days. Above 0 means the stock beat the market.",
      ],
      example:
        "Example: on 1 Oct evening the Radar ranked KLBF #1 → the test buys KLBF at the 2 Oct open → sells at the close 5 trading days later → if KLBF gained 3% while IHSG gained 1%, that trade counts as +2% vs IHSG (minus fees).",
      groupHelp: {
        TOP_PICKS: "The Radar's best-ranked stocks each day — what you'd get by following the ranking.",
        HIGH_SCORE: "Every stock that scored 50 or more, any day.",
        ACCUMULATION: "Days the stock's broker flow was rated Accumulation.",
        NEUTRAL: "Days rated Neutral.",
        DISTRIBUTION: "Days rated Distribution.",
        ALL: "Every tracked stock, every day — the baseline. A useful signal must beat this row.",
      } as Record<string, string>,
      columnsHelp:
        "Big number: average result vs IHSG (green = beat the market). \"made money\": share of trades that were positive after fees. \"trades\": how many were measured.",
      howTitle: "How to read this",
      how: [
        "“vs IHSG” is the average result minus what IHSG did over the same days: above 0 means the group beat the market.",
        "“made money” counts trades that were positive after fees.",
        "The baseline (all tracked stocks) shows what you'd get without the Radar. A useful signal has to beat it, not just be positive.",
        "Trades on the same day move together (one market mood), so the real sample size is the number of days, not trades.",
      ],
      unavailable: "The track record is unavailable right now.",
      none: "Not enough broker history yet to replay the Radar.",
    },
    help: "How to read this",
    helpItems: [
      { term: "Streak", text: "How many days in a row the 5 biggest buyers bought more than the 5 biggest sellers sold. Longer = steadier buying." },
      { term: "Lead broker", text: "The single broker that kept net buying the most days in a row, and its average buy price." },
      { term: "Foreign", text: "What foreign investors bought minus what they sold in this period." },
      { term: "vs buyers' cost", text: "Today's price compared with what the main buyers paid. Close to 0% means the price hasn't run away from them yet; far above can mean they're ready to take profit." },
      { term: "Score", text: "Adds up the points above (see 'Why this score'). It ranks what has happened; it doesn't predict. See “Track record” for how it has done so far." },
    ],
    whyScore: "Why this score",
    say: {
      buying: (days: number) => `The biggest brokers have been net buying ${days} day${days === 1 ? "" : "s"} in a row.`,
      selling: (days: number) => `The biggest brokers have been net selling ${days} day${days === 1 ? "" : "s"} in a row.`,
      noStreak: "No clear buying or selling streak right now.",
      lead: (code: string, name: string, days: number, price: string) =>
        `${code}${name ? ` (${name})` : ""} bought ${days} day${days === 1 ? "" : "s"} running, at about Rp${price} on average.`,
      foreignBought: (value: string) => `Foreign investors bought ${value} more than they sold.`,
      foreignSold: (value: string) => `Foreign investors sold ${value} more than they bought.`,
      aboveCost: (pct: string) => `The price is ${pct} above what the main buyers paid.`,
      belowCost: (pct: string) => `The price is ${pct} below what the main buyers paid.`,
    },
    streaksIntro:
      "Brokers that net bought the same stock at least 3 days in a row, up to the latest data. The longest runs first.",
    streakLine: (days: number) => `${days} days in a row`,
    streakAvg: (price: string) => `avg Rp${price}`,
    streakNet: (value: string) => `net ${value}`,
    streakGap: (pct: string) => `price ${pct} vs their cost`,
    noStreaks: "No broker has bought the same stock 3 days in a row yet.",
  },
  hsc: {
    badge: "HSC",
    title:
      "High Shareholding Concentration: a few holders own almost all shares, so prices and broker flow can be steered. IDX removes these stocks from the IHSG from 2 Nov 2026; they still trade.",
    note: "This stock is on IDX's High Shareholding Concentration (HSC) list: a few holders own almost all shares, so its price and broker flow can be moved by a small group. IDX removes HSC stocks from the IHSG from 2 November 2026; they keep trading.",
    filter: "HSC",
    filterAll: "All stocks",
    filterExclude: "Hide HSC",
    filterOnly: "HSC only",
  },
  brokers: {
    title: "Brokers",
    intro: "Brokers active in the tracked stocks over the last month, most active first. Open one to see what it's collecting or selling.",
    code: "Broker",
    stocks: "Stocks",
    gross: "Traded (buy + sell)",
    net: "Net",
    empty: "No broker data yet.",
    back: "← All brokers",
    window: "Look-back",
    windowLabel: (days: number) => `${days} days`,
    period: (from: string, to: string) => `${from} – ${to} · tracked stocks only`,
    totalGross: "Traded",
    totalNet: "Net bought",
    accumulating: "Accumulating",
    accumulatingHint: "Stocks this broker bought more of than it sold, biggest first.",
    distributing: "Distributing",
    distributingHint: "Stocks this broker sold more of than it bought.",
    none: "None in this period.",
    boughtLine: (value: string, lots: string, price: string) => `Net bought ${value} (${lots}) at about Rp${price} on average.`,
    soldLine: (value: string, lots: string, price: string) => `Net sold ${value} (${lots}) at about Rp${price} on average.`,
    gapAbove: (pct: string) => `Price is ${pct} above that.`,
    gapBelow: (pct: string) => `Price is ${pct} below that.`,
    streak: (days: number) => `Buying ${days} day${days === 1 ? "" : "s"} in a row now.`,
    activeDays: (days: number) => `Active on ${days} day${days === 1 ? "" : "s"}.`,
    foreign: (value: string) => `Foreign investors through this broker: ${value}.`,
    note: "Covers only the stocks that get broker data. Average prices are this broker's in this period, not its full position.",
  },
  watchlist: {
    title: "My watchlist",
    intro: "Stocks you starred, with today's price and what the big brokers have been doing over the last 10 broker days. The list is saved to your account, so it's the same on every phone and computer you log in on.",
    button: { add: "Add to my list", remove: "On my list" },
    addLabel: "Add a stock",
    addPlaceholder: "e.g. BBCA",
    add: "Add",
    remove: "Remove",
    empty: "Your list is empty. Type a stock code above, or press ☆ Add to my list on any stock page.",
    unknown: (ticker: string) => `${ticker} isn't a listed stock we know.`,
    full: (max: number) => `Your list is full (${max} stocks). Remove one first.`,
    count: (n: number, max: number) => `${n} of ${max}`,
    todayAsOf: (time: string) => `prices today, as of ${time} (Yahoo)`,
    brokerFlow: "Broker flow (10 days)",
    loadingBrokers: "Loading broker flow…",
    notTracked: "No broker data for this stock yet. Open it and press \"Load broker data\".",
    noBrokerData: "Not enough broker days yet.",
    brokerUnavailable: "Broker flow unavailable right now.",
    leadBroker: (code: string, days: number) => `${code} buying ${days} day${days === 1 ? "" : "s"} in a row`,
    note: "Broker flow describes what already happened; it is not a prediction or advice.",
  },
  insiders: {
    title: "Insider transactions",
    latest: (date: string) => `latest ${date}`,
    intro: "Directors, commissioners and big shareholders (5%+) must report when they buy or sell. Buying with their own money is a mildly positive sign; selling happens for many reasons. Reports arrive days after the trade.",
    bought: (shares: string) => `bought ${shares} shares`,
    sold: (shares: string) => `sold ${shares} shares`,
    cross: (shares: string) => `transferred ${shares} shares in a negotiated (cross) trade`,
    other: (action: string, shares: string) => `${action}: ${shares} shares`,
    atPrice: (price: string) => ` at ${price}`,
    nowHolds: (pct: string) => `now holds ${pct}`,
    foreign: "foreign",
    none: "No insider transactions reported.",
    notLoaded: "Insider data isn't loaded yet (today's limit may be used up). Try again tomorrow.",
    unavailable: "Insider data is unavailable right now.",
    more: (shown: number, total: number) => `Latest ${shown} of ${total} reports.`,
    checked: (time: string) => `Checked ${time}`,
  },
  summary: {
    title: "Summary",
    glance: "Broker flow at a glance",
    periods: { "10D": "Last 10 days", "1M": "1 month", "3M": "3 months" } as Record<string, string>,
    periodsInline: { "10D": "the last 10 days", "1M": "1 month", "3M": "3 months" } as Record<string, string>,
    fewDays: (days: number) => `only ${days} days of data`,
    brokersLine: (parts: string) => `Big brokers: ${parts}.`,
    flowWords: { ACCUMULATION: "collecting", NEUTRAL: "balanced", DISTRIBUTION: "selling" },
    over: (word: string, periods: string) => `${word} over ${periods}`,
    and: " and ",
    levels: "Price levels",
    monthRange: "1-month range",
    yearRange: "52-week range",
    notTracked: "No broker data for this stock yet.",
    live: {
      trendUp: "Uptrend: price is above its rising 20-day average ({ma20})",
      trendDown: "Downtrend: price is below its falling 20-day average ({ma20})",
      trendSideways: "No clear trend: price is moving around its 20-day average ({ma20})",
      costFar: "Watch out: price is {gap} above what last month's main buyers paid ({price}) — they may take profit",
      costNear: "Price is close to what last month's main buyers paid ({price})",
      costBelow: "Price is {gap} below what last month's main buyers paid ({price}) — they are at a loss",
      nearHigh: "Near its 52-week high ({price})",
      nearLow: "Near its 52-week low ({price})",
      liveNote: "Lines marked ● use today's price (live, or today's close after the market closes); broker and foreign flow are up to the last stored day.",
    },
    note: "Describes what already happened — not a prediction or advice.",
    unavailable: "Summary unavailable right now.",
  },
  auth: {
    email: "Email",
    password: "Password",
    newPassword: "New password",
    confirm: "Repeat password",
    passwordHint: "At least 8 characters.",
    loginTitle: "Log in",
    loginIntro: "Log in to use IDX Intelligence.",
    loginButton: "Log in",
    noAccount: "No account yet?",
    registerLink: "Register",
    forgotLink: "Forgot password?",
    registerTitle: "Create an account",
    registerIntro: "Your email and a password are all it takes. My list is saved to your account, so it follows you to every device.",
    registerButton: "Create account",
    haveAccount: "Already registered?",
    loginLink: "Log in",
    forgotTitle: "Forgot your password?",
    forgotIntro: "Enter your account's email and we'll send a link to choose a new password. The link works for 30 minutes.",
    forgotButton: "Send reset link",
    forgotSent: "If that email has an account, a reset link is on its way. Check your inbox (and the spam folder).",
    resetTitle: "Choose a new password",
    resetIntro: "After this you'll be logged out on every device and can log in with the new password.",
    resetButton: "Save new password",
    resetDone: "Password changed. Log in with your new password.",
    registered: "Account created! Log in with your email and password.",
    backToLogin: "← Back to log in",
    logout: "Log out",
    loggedInAs: (email: string) => `Logged in as ${email}`,
    errors: {
      WRONG_LOGIN: "Wrong email or password.",
      TOO_MANY_ATTEMPTS: "Too many attempts. Wait a few minutes and try again.",
      INVALID_EMAIL: "That doesn't look like an email address.",
      EMAIL_TAKEN: "There's already an account with this email. Log in, or reset the password.",
      PASSWORD_TOO_SHORT: "The password needs at least 8 characters.",
      PASSWORD_TOO_LONG: "The password is too long (max 72 characters).",
      PASSWORD_MISMATCH: "The two passwords don't match.",
      INVALID_LINK: "This reset link is invalid, already used or expired. Ask for a new one.",
      EMAIL_FAILED: "The email couldn't be sent right now. Try again later.",
    } as Record<string, string>,
    genericError: "Something went wrong. Try again in a minute.",
  },
  shortlist: {
    title: "Worth a look",
    intro:
      "Tracked stocks checked against 8 conditions that professional traders and research look at. The more checks a stock passes, the higher it ranks. Every check is shown, so you can see exactly why a stock is here — and what speaks against it.",
    disclaimerTitle: "Not investment advice",
    disclaimer:
      "This is a research shortlist, not a recommendation to buy or sell. Passing all checks does not mean a stock will rise: markets surprise, and these checks describe the past. Always do your own research, only invest money you can afford to lose, and decide your exit (stop-loss) before you buy.",
    methodTitle: "How a stock gets on this list",
    groups: [
      { name: "Trend", text: "Price above its 50-day average, which is above a rising 200-day average, and within 25% of its 52-week high. Professional swing traders (e.g. the \"trend template\" popularised by Mark Minervini) only look at stocks already in an uptrend." },
      { name: "Strength", text: "Beat IHSG over the last 3 months. Stocks that outperformed for 3–12 months tend to keep doing so for a while — the \"momentum\" effect, documented in academic research since 1993 in many countries. It works over months, not days." },
      { name: "Big money", text: "Big brokers collecting over the last 10 broker days and the last month, and foreign investors net buying (bandarmologi). Large players can't buy all at once, so their buying shows up over days." },
      { name: "Risk", text: "Price at most 10% above what the main buyers paid (not stretched), at least Rp5 miliar traded per day (liquid), not on the HSC list and not already up more than 15% in 5 days (no chasing)." },
    ],
    scope: (tracked: number) =>
      `Only the ${tracked} stocks with broker data can be checked; add a stock with “Load broker data” on its page.`,
    passed: (n: number, total: number) => `${n} of ${total} checks`,
    rank: (n: number) => `#${n}`,
    othersTitle: (n: number) => `Other tracked stocks (${n})`,
    measuredTitle: "Measured so far",
    measured: (top: string, all: string, days: number) =>
      `Buying the daily top 10 and holding them 5 trading days did ${top} vs IHSG; buying any tracked stock did ${all} (${days} days of history).`,
    tooEarly: "Far too little history to tell whether the list helps. Treat it as a starting point for research, not a signal.",
    groupsRecord: {
      TOP_PICKS: "Daily top 10",
      ALL_CHECKS: "Passed all checks",
      ALMOST_ALL: "Passed all but one",
      ALL: "All tracked stocks (baseline)",
    } as Record<string, string>,
    dataAsOf: (prices: string, brokers: string) => `Prices up to ${prices} · broker data up to ${brokers}`,
    empty: "No tracked stocks with enough history yet.",
    unavailable: "The shortlist is unavailable right now.",
  },
  dashboard: {
    openIhsg: "Open IHSG chart →",
    seeAll: "See all →",
    tabs: { gainers: "Top gainers", losers: "Top losers", active: "Most active" },
    trend: "Today",
    breadthHint: "Click a number to list those stocks",
    moversNote: "Gainers and losers only count stocks with at least Rp1 miliar traded that day.",
    liveAsOf: (time: string) => `Today, as of ${time}`,
    closeOf: (date: string) => `Close of ${date}`,
    liveNote: "Today's top 10 from IDX Edge, updated every 10 minutes during the session. “Thin” = under Rp1 miliar traded today: big moves on little money can reverse quickly. After the evening import this switches back to the full end-of-day lists.",
    thin: "thin",
    thinTitle: "Under Rp1 miliar traded today",
    ihsgDelayed: "{time} · delayed about 10 minutes",
  },
  ihsg: {
    title: "IDX Composite (IHSG)",
    back: "← Market overview",
    cannotLoad: "Cannot load the IHSG right now. The server may be waking up — try again in a minute.",
  },
  theme: {
    label: "Theme",
    light: "Light",
    dark: "Dark",
    system: "System",
  },
  language: {
    label: "Language",
    en: "English",
    id: "Bahasa Indonesia",
  },
  overview: {
    ihsg: "IDX Composite (IHSG)",
    ihsgMissing: "Not imported yet — run the price import.",
    breadth: "Market breadth",
    up: "Up",
    down: "Down",
    flat: "Flat",
    topGainers: "Top gainers",
    topLosers: "Top losers",
    mostActive: "Most active",
    ticker: "Ticker",
    last: "Last",
    change: "Change",
    estValue: "Est. value (Rp)",
    noStocks: "No stocks to show.",
  },
  stockList: {
    title: "All stocks",
    search: "Search code or name",
    searchButton: "Search",
    allSectors: "All sectors",
    sortBy: "Sort by",
    sorts: {
      marketCap: "Market cap",
      change: "Change today",
      value: "Traded value",
      volume: "Volume",
      price: "Price",
      ticker: "Code",
    },
    order: { desc: "High to low", asc: "Low to high" },
    found: (total: number) => `${total} stocks`,
    todayPrices: (time: string) =>
      `Prices and changes are today's, as of ${time} (Yahoo, about 10 minutes delayed while the market trades). Traded value fills in after the evening import.`,
    stock: "Stock",
    sector: "Sector",
    last: "Last",
    change: "Change",
    volume: "Volume",
    value: "Est. value",
    marketCap: "Market cap",
    brokerTag: "Broker data",
    brokerTagTitle: "Broker summaries are imported for this stock",
    noMatch: "No stocks match.",
    previous: "← Previous",
    next: "Next →",
    pageOf: (page: number, pages: number) => `Page ${page} of ${pages}`,
    unknownSector: "—",
  },
  stock: {
    back: "← Market overview",
    cannotLoad: (ticker: string) => `Cannot load data for ${ticker} right now. The server may be waking up — try again in a minute.`,
    closeOf: (date: string) => `Close of ${date}`,
    vol: "Vol",
    estValue: "Est. value",
    dailyPrice: "Daily price",
    priceRange: "Price range",
    priceTable: (days: number) => `Price table (${days} candles)`,
    date: "Date",
    open: "Open",
    high: "High",
    low: "Low",
    close: "Close",
    volume: "Volume",
    prices: "Prices",
    brokerData: "Broker data",
    demoHistory: "Demo history is invented, skips weekends but not IDX holidays, and is not market data.",
    notAdjusted: "Prices are not adjusted for stock splits or dividends.",
    brokerUnavailable: "Broker activity is unavailable right now.",
    brokerNotTracked:
      "Broker data isn't imported for this stock. To stay within the free data plan, broker summaries cover a tracked list of stocks; prices cover all of them.",
    loadBroker: "Load broker data",
    loadBrokerHint: "Fetches about the last month and keeps this stock updated daily. Limited to a few stocks per day.",
    tracking: {
      TRACKED: "Loading broker data in the background — reload this page in about a minute.",
      QUEUED: "Today's free data limit is used up. This stock is now tracked; its broker data loads after midnight (00:00 WIB) while the backend is running.",
      ALREADY_TRACKED: "This stock already gets broker data; it may still be loading.",
      UNKNOWN_STOCK: "This stock can't be tracked.",
      TOTAL_LIMIT_REACHED: "The tracked list is full (free data plan limit). Try again after it's reviewed.",
      DAILY_LIMIT_REACHED: "Today's limit for new stocks is reached. Try again tomorrow.",
      FAILED: "Couldn't start loading broker data. Check that the backend is running.",
    },
  },
  chart: {
    noHistory: "No price history available.",
    date: "Date",
    open: "O",
    high: "H",
    low: "L",
    close: "C",
    change: "Chg",
    volume: "Vol",
    concentration: "Top-5 conc.",
    buyersSellers: "Buyers/Sellers",
    foreignNet: "Foreign net",
    concentrationSeries: "Top-5 conc.",
    foreignSeries: "Foreign net (cum.)",
  },
  broker: {
    title: (range: string) => `Broker activity · ${range}`,
    periods: { "1D": "Last day", "1W": "1 week", "1M": "1 month", "3M": "3 months" },
    periodLabel: "Broker period",
    singleDay: (date: string) => `${date} only (one trading day)`,
    totalOver: (from: string, to: string, days: number) => `Totals over ${from} – ${to} (${days} day${days === 1 ? "" : "s"} with broker data)`,
    noDays: "No broker data in this period yet",
    demoTag: "DEMO DATA",
    coverage: (brokerDays: number, tradingDays: number) =>
      `Broker data covers ${brokerDays} of ${tradingDays} trading days in this range. History is still being imported (newest days first) and fills in a little more each day.`,
    status: { ACCUMULATION: "Accumulation", NEUTRAL: "Neutral", DISTRIBUTION: "Distribution" },
    disclaimer:
      "Rule-based description of past broker flow, not a forecast or a recommendation to buy or sell. The rules have not been backtested yet.",
    showTables: "Top buyers & sellers (by broker)",
    topBuyers: "Top net buyers",
    topSellers: "Top net sellers",
    broker: "Broker",
    netLot: "Net lot",
    netValue: "Net value",
    avgBuy: "Avg buy",
    avgSell: "Avg sell",
    none: "None in this period.",
    howToRead: "How to read this",
    terms: [
      {
        name: "Top-5 concentration",
        text: "Each day: lots net bought by the 5 biggest net buyers, minus lots net sold by the 5 biggest net sellers, as a share of that day's volume. Above zero means buying is concentrated in a few brokers while selling is spread across many — a common sign of accumulation. Below zero is the reverse.",
      },
      {
        name: "Buyers / sellers",
        text: "How many brokers ended the day as net buyers vs net sellers. Fewer buyers than sellers means the buying sits in fewer hands.",
      },
      {
        name: "Foreign net",
        text: "Net value bought by foreign investors, across all brokers they traded through.",
      },
      {
        name: "Average price",
        text: "Value divided by volume for that broker over the period: roughly where its position was built (buyers) or unwound (sellers).",
      },
    ],
  },
  reasons: {
    NO_BROKER_DATA: () => "No broker data for this period yet.",
    TOO_FEW_BROKER_DAYS: (v) =>
      `Only ${v.days} trading day${n(v.days) === 1 ? "" : "s"} of broker data so far; an assessment needs at least ${v.minDays}.`,
    CONCENTRATION_ACCUMULATION: (v, f) =>
      `The top 5 net buyers absorbed more than the top 5 net sellers released: on average ${f.signedFraction(n(v.avgConcentration))} of daily volume (accumulation above +${f.fraction(n(v.threshold))}).`,
    CONCENTRATION_DISTRIBUTION: (v, f) =>
      `The top 5 net sellers released more than the top 5 net buyers absorbed: on average ${f.signedFraction(n(v.avgConcentration))} of daily volume (distribution below -${f.fraction(n(v.threshold))}).`,
    CONCENTRATION_BALANCED: (v, f) =>
      `The top 5 net buyers and sellers were roughly balanced: on average ${f.signedFraction(n(v.avgConcentration))} of daily volume.`,
    BREADTH_BUYING_CONCENTRATED: (v, f) =>
      `Fewer brokers were net buying than net selling on ${f.fraction(n(v.share))} of days — buying concentrated in fewer hands.`,
    BREADTH_SELLING_CONCENTRATED: (v, f) =>
      `Fewer brokers were net buying than net selling on only ${f.fraction(n(v.share))} of days — selling concentrated in fewer hands.`,
    BREADTH_NEUTRAL: (v, f) => `Fewer brokers were net buying than net selling on ${f.fraction(n(v.share))} of days.`,
    FOREIGN_UNAVAILABLE: () => "Foreign investor flow is not available for this period yet.",
    FOREIGN_NET_BUY: (v, f) =>
      `Foreign investors net bought ${f.rupiah(n(v.value))} over the period${n(v.daysWithData) < n(v.days) ? ` (${v.daysWithData} of ${v.days} days with foreign data)` : ""}.`,
    FOREIGN_NET_SELL: (v, f) =>
      `Foreign investors net sold ${f.rupiah(Math.abs(n(v.value)))} over the period${n(v.daysWithData) < n(v.days) ? ` (${v.daysWithData} of ${v.days} days with foreign data)` : ""}.`,
    TOP_BUYERS_AVG_PRICE: (v, f) =>
      `The top 5 buyers' average buy price is Rp${f.price(n(v.avgPrice))}; the last price is ${f.fraction(Math.abs(n(v.gap)))} ${n(v.gap) >= 0 ? "above" : "below"} it.`,
    BREAKOUT_20D: (v, f) => `Closed above its 20-day high (${f.price(n(v.level))})`,
    VOLUME_SPIKE: (v, f) => `Volume ${f.decimal1(n(v.ratio))}× its 20-day average`,
    CLOSE_NEAR_HIGH: () => "Closed near the day's high",
    UP_TODAY: (v, f) => `Up ${f.signedFraction(n(v.change))} today`,
    ABOVE_RISING_MA20: (v, f) => `Above a rising 20-day average (${f.price(n(v.ma20))})`,
    BROKERS_NET_BUYING: (v, f) =>
      `Top-5 brokers net buying: avg ${f.signedFraction(n(v.avgConcentration))} of volume over the last ${v.days} days`,
    BROKERS_NET_SELLING: (v, f) =>
      `Top-5 brokers net selling: avg ${f.signedFraction(n(v.avgConcentration))} of volume over the last ${v.days} days`,
    FOREIGN_NET_BUY_RECENT: (v, f) => `Foreign investors net bought ${f.rupiah(n(v.value))} over the last ${v.days} days`,
    FOREIGN_NET_SELL_RECENT: (v, f) =>
      `Foreign investors net sold ${f.rupiah(Math.abs(n(v.value)))} over the last ${v.days} days`,
    RADAR_BROKER_DATA_BEHIND: (v) => `Broker data for this stock only runs to ${v.latestDay}; it's still loading.`,
    RADAR_ACCUMULATION_STREAK: (v) => `Top-5 brokers net bought ${v.days} day${n(v.days) === 1 ? "" : "s"} in a row`,
    RADAR_DISTRIBUTION_STREAK: (v) => `Top-5 brokers net sold ${v.days} day${n(v.days) === 1 ? "" : "s"} in a row`,
    RADAR_CONCENTRATED_BUYING: (v, f) =>
      `Buying concentrated in a few brokers: avg ${f.signedFraction(n(v.avgConcentration))} of volume over ${v.days} days`,
    RADAR_CONCENTRATED_SELLING: (v, f) =>
      `Selling concentrated in a few brokers: avg ${f.signedFraction(n(v.avgConcentration))} of volume over ${v.days} days`,
    RADAR_FOREIGN_BUYING: (v, f) => `Foreign investors net bought ${f.rupiah(n(v.value))} over ${v.days} days`,
    RADAR_FOREIGN_SELLING: (v, f) => `Foreign investors net sold ${f.rupiah(Math.abs(n(v.value)))} over ${v.days} days`,
    RADAR_BROKER_STREAK: (v, f) =>
      `${v.broker}${v.name ? ` (${v.name})` : ""} net bought ${v.days} days in a row, avg price ${f.price(n(v.averagePrice))}`,
    RADAR_ABOVE_RISING_MA20: (v, f) => `Price above a rising 20-day average (${f.price(n(v.ma20))})`,
    RADAR_NEAR_BUYERS_COST: (v, f) =>
      `Price is ${f.fraction(n(v.gap))} above the top buyers' average cost (${f.price(n(v.averagePrice))}): not stretched`,
    RADAR_FAR_ABOVE_BUYERS_COST: (v, f) =>
      `Price is ${f.fraction(n(v.gap))} above the top buyers' average cost (${f.price(n(v.averagePrice))}): they may take profit`,
    RADAR_BELOW_BUYERS_COST: (v, f) =>
      `Price is ${f.fraction(Math.abs(n(v.gap)))} below the top buyers' average cost (${f.price(n(v.averagePrice))})`,
    HSC: () => "On IDX's High Shareholding Concentration list: a few holders own almost all shares",
    LOW_LIQUIDITY: (v, f) =>
      `Low liquidity: averages ${f.rupiah(n(v.avgValue))} traded per day (minimum ${f.rupiah(n(v.minValue))})`,
    NEAR_LIMIT_UP: (v, f) => `Up ${f.signedFraction(n(v.change))} today, likely at the daily limit: hard to buy at the open`,
    CHASING: (v, f) => `Already up ${f.signedFraction(n(v.change5d))} in 5 days: high risk of chasing`,
    SUM_TREND_UP: (v, f) => `Uptrend: price is above its rising 20-day average (${f.price(n(v.ma20))})`,
    SUM_TREND_DOWN: (v, f) => `Downtrend: price is below its falling 20-day average (${f.price(n(v.ma20))})`,
    SUM_TREND_SIDEWAYS: (v, f) => `No clear trend: price is moving around its 20-day average (${f.price(n(v.ma20))})`,
    SUM_FOREIGN_BUYING: (v, f) => `Foreign investors net bought ${f.rupiah(n(v.value))} over the last 3 months`,
    SUM_FOREIGN_SELLING: (v, f) => `Foreign investors net sold ${f.rupiah(Math.abs(n(v.value)))} over the last 3 months`,
    SUM_FAR_ABOVE_BUYERS_COST: (v, f) =>
      `Watch out: price is ${f.fraction(n(v.gap))} above what last month's main buyers paid (${f.price(n(v.averagePrice))}) — they may take profit`,
    SUM_NEAR_BUYERS_COST: (v, f) =>
      `Price is close to what last month's main buyers paid (${f.price(n(v.averagePrice))})`,
    SUM_BELOW_BUYERS_COST: (v, f) =>
      `Price is ${f.fraction(Math.abs(n(v.gap)))} below what last month's main buyers paid (${f.price(n(v.averagePrice))}) — they are at a loss`,
    SUM_VS_IHSG: (v, f) =>
      v.excess3m != null
        ? `${n(v.excess3m) >= 0 ? "Beat" : "Lagged"} IHSG by ${f.fraction(Math.abs(n(v.excess3m)))} over 3 months` +
          (v.excess1m != null ? ` (${f.signedFraction(n(v.excess1m))} vs IHSG over 1 month)` : "")
        : `${f.signedFraction(n(v.excess1m))} vs IHSG over 1 month`,
    SUM_VOLUME_HIGH: (v, f) => `Unusual volume: ${f.decimal1(n(v.ratio))}× the normal daily volume`,
    SUM_VOLUME_LOW: (v, f) => `Quiet trading: only ${f.decimal1(n(v.ratio))}× the normal daily volume`,
    SUM_NEAR_52W_HIGH: (v, f) => `Near its 52-week high (${f.price(n(v.high))})`,
    SUM_NEAR_52W_LOW: (v, f) => `Near its 52-week low (${f.price(n(v.low))})`,
    SUM_INSIDER_BUYING: (v) => `Insiders reported ${v.bought} purchase${n(v.bought) === 1 ? "" : "s"} in the last 30 days`,
    SUM_INSIDER_SELLING: (v) => `Insiders reported ${v.sold} sale${n(v.sold) === 1 ? "" : "s"} in the last 30 days`,
    SUM_HSC: () => "Watch out: on IDX's High Shareholding Concentration list — a few holders own almost all shares",
    SL_LONG_TREND: (v, f) =>
      v.pass
        ? `Long-term uptrend: price above its 50-day average (${f.price(n(v.ma50))}), which is above a rising 200-day average (${f.price(n(v.ma200))})`
        : v.ma200 == null
          ? "Not enough price history for a 200-day average yet"
          : `No long-term uptrend: price, 50-day (${f.price(n(v.ma50))}) and 200-day (${f.price(n(v.ma200))}) averages aren't lined up upwards`,
    SL_NEAR_HIGH: (v, f) =>
      v.fromHigh == null
        ? "Not enough history for a 52-week high"
        : v.pass
          ? `Close to its 52-week high (${f.price(n(v.high))}): ${f.fraction(Math.abs(n(v.fromHigh)))} below it`
          : `${f.fraction(Math.abs(n(v.fromHigh)))} below its 52-week high (${f.price(n(v.high))}) — far from its highs`,
    SL_STRENGTH: (v, f) =>
      v.excess3m == null
        ? "Can't compare with IHSG yet"
        : v.pass
          ? `Stronger than the market: beat IHSG by ${f.fraction(n(v.excess3m))} over 3 months`
          : `Weaker than the market: lagged IHSG by ${f.fraction(Math.abs(n(v.excess3m)))} over 3 months`,
    SL_BROKERS: (v) =>
      v.pass
        ? "Big brokers have been collecting, both over the last 10 broker days and the last month"
        : `Big brokers not collecting on both (10 days: ${flowWord(v.tenDays)}, 1 month: ${flowWord(v.month)})`,
    SL_FOREIGN: (v, f) =>
      v.value == null
        ? "Not enough foreign-flow data for the last month"
        : v.pass
          ? `Foreign investors net bought ${f.rupiah(n(v.value))} over the last month`
          : `Foreign investors net sold ${f.rupiah(Math.abs(n(v.value)))} over the last month`,
    SL_NOT_STRETCHED: (v, f) =>
      v.gap == null
        ? "No buyers' cost to compare with"
        : v.pass
          ? `Not stretched: price ${f.signedFraction(n(v.gap))} vs what last month's main buyers paid (${f.price(n(v.averagePrice))})`
          : `Stretched: price ${f.fraction(n(v.gap))} above what last month's main buyers paid (${f.price(n(v.averagePrice))}) — they may take profit`,
    SL_LIQUID: (v, f) =>
      v.pass
        ? `Liquid: ${f.rupiah(n(v.avgValue))} traded per day on average`
        : `Thinly traded: ${f.rupiah(n(v.avgValue))} per day (minimum ${f.rupiah(n(v.minValue))}) — hard to buy or sell without moving the price`,
    SL_SAFE: (v, f) =>
      v.pass
        ? "Not on the HSC list and not already up more than 15% in 5 days"
        : v.hsc
          ? "On IDX's High Shareholding Concentration list — a few holders own almost all shares"
          : `Already up ${f.signedFraction(n(v.change5d))} in 5 days — buying now would be chasing`,
  } satisfies Record<string, ReasonText>,
};

export type Dictionary = typeof en;

/**
 * Labels handed to Client Components must be plain strings: functions can't be sent from the
 * server to the browser (React throws "Functions cannot be passed directly to Client Components").
 * Using this type in a client prop turns that runtime crash into a type error.
 */
export type ClientLabels<T> = { [K in keyof T]: T[K] extends string ? T[K] : never };
