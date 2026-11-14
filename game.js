(() => {
  const SAVE_KEY = 'obelisk-protocol-save-v1';
  const upgrades = [
    { id: 'servo', name: 'Servo Gauntlet', icon: '⌁', detail: 'Increase manual & bot extraction power.', baseCost: 25, growth: 1.22, tap: 2, rate: 0 },
    { id: 'drone', name: 'Mining Drone', icon: '⬡', detail: 'A dependable autonomous ore collector.', baseCost: 15, growth: 1.17, tap: 0, rate: 1 },
    { id: 'laser', name: 'Pulse Laser', icon: '⟢', detail: 'Focused beams fracture the obelisk faster.', baseCost: 110, growth: 1.2, tap: 0, rate: 7 },
    { id: 'quantum', name: 'Quantum Extractor', icon: '◈', detail: 'Harvest matter from adjacent timelines.', baseCost: 700, growth: 1.23, tap: 0, rate: 45 }
  ];
  const milestones = [
    { target: 100, title: 'First contact', copy: 'Mine 100 Stellar Ore', reward: 1 },
    { target: 1000, title: 'Signal detected', copy: 'Mine 1,000 Stellar Ore', reward: 1 },
    { target: 10000, title: 'Deep resonance', copy: 'Mine 10,000 Stellar Ore', reward: 2 },
    { target: 50000, title: 'Obelisk awakened', copy: 'Mine 50,000 Stellar Ore', reward: 3 }
  ];
  const defaults = { ore: 0, cores: 0, totalMined: 0, runMined: 0, prestigeCount: 0, levels: { servo: 0, drone: 0, laser: 0, quantum: 0 }, claimed: [], bot: false, strategy: 'balanced', last: Date.now() };
  let state = loadState();
  let lastFrame = Date.now();
  let lastBotAction = 0;
  let lastRender = 0;
  let saveTimer;
  let toastTimer;
  const $ = id => document.getElementById(id);
  const format = value => {
    if (!Number.isFinite(value)) return '∞';
    if (value < 1000) return Math.floor(value).toLocaleString('en-US');
    const units = [['T', 1e12], ['B', 1e9], ['M', 1e6], ['K', 1e3]];
    for (const [suffix, threshold] of units) if (value >= threshold) return (value / threshold).toFixed(value >= threshold * 100 ? 0 : 1).replace(/\.0$/, '') + suffix;
    return String(Math.floor(value));
  };
  const precise = value => value < 100 ? value.toFixed(value % 1 ? 1 : 0) : format(value);
  function loadState() {
    try {
      const saved = JSON.parse(localStorage.getItem(SAVE_KEY));
      if (!saved) return { ...defaults, levels: { ...defaults.levels }, claimed: [] };
      const merged = { ...defaults, ...saved, levels: { ...defaults.levels, ...(saved.levels || {}) }, claimed: Array.isArray(saved.claimed) ? saved.claimed : [] };
      const away = Math.min(8 * 60 * 60, Math.max(0, (Date.now() - (saved.last || Date.now())) / 1000));
      const awayGain = passiveRate(merged) * away;
      if (awayGain > 1) {
        merged.ore += awayGain;
        merged.totalMined += awayGain;
        merged.runMined += awayGain;
        merged._away = awayGain;
      }
      return merged;
    } catch { return { ...defaults, levels: { ...defaults.levels }, claimed: [] }; }
  }
  function save() {
    state.last = Date.now();
    const clean = { ...state };
    delete clean._away;
    try { localStorage.setItem(SAVE_KEY, JSON.stringify(clean)); $('save-status').textContent = 'SAVED LOCALLY'; }
    catch { $('save-status').textContent = 'SAVE UNAVAILABLE'; }
  }
  function tapPower(s = state) { return 1 + upgrades.reduce((sum, item) => sum + item.tap * (s.levels[item.id] || 0), 0); }
  function passiveRate(s = state) { return upgrades.reduce((sum, item) => sum + item.rate * (s.levels[item.id] || 0), 0) * (1 + (s.cores || 0) * 0.05); }
  function cost(item) { return Math.ceil(item.baseCost * Math.pow(item.growth, state.levels[item.id] || 0)); }
  function addOre(amount) {
    if (!(amount > 0) || !Number.isFinite(amount)) return;
    state.ore += amount;
    state.totalMined += amount;
    state.runMined += amount;
    checkMilestones();
  }
  function log(message, bot = false) {
    const host = $('event-log');
    const row = document.createElement('div');
    row.className = 'log-entry' + (bot ? ' bot-log' : '');
    const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    row.innerHTML = `<time>${time}</time><span>${message}</span>`;
    host.prepend(row);
    while (host.children.length > 4) host.lastElementChild.remove();
  }
  function notify(message) {
    const toast = $('toast');
    toast.textContent = message;
    toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('show'), 2200);
  }
  function checkMilestones() {
    milestones.forEach((milestone, index) => {
      if (state.totalMined >= milestone.target && !state.claimed.includes(index)) {
        state.claimed.push(index);
        state.cores += milestone.reward;
        log(`Milestone: ${milestone.title} · +${milestone.reward} nanite core`, true);
        notify(`Milestone reached · +${milestone.reward} nanite core${milestone.reward > 1 ? 's' : ''}`);
      }
    });
  }
  function renderUpgrades() {
    const host = $('upgrade-list');
    host.innerHTML = '';
    upgrades.forEach(item => {
      const level = state.levels[item.id] || 0;
      const price = cost(item);
      const affordable = state.ore >= price;
      const card = document.createElement('div');
      card.className = 'upgrade-card' + (affordable ? ' affordable' : '');
      card.innerHTML = `<div class="upgrade-icon">${item.icon}</div><div class="upgrade-info"><h4>${item.name}</h4><p>${item.detail}</p><div class="upgrade-level">LVL ${String(level).padStart(2, '0')} · ${item.rate ? `+${format(item.rate * (1 + state.cores * .05))}/SEC` : `+${item.tap} CLICK`}</div></div><div class="upgrade-buy"><button data-buy="${item.id}" ${affordable ? '' : 'disabled'}><span class="coin">✦</span> ${format(price)}</button><small>STELLAR ORE</small></div>`;
      host.appendChild(card);
    });
    host.querySelectorAll('[data-buy]').forEach(button => button.addEventListener('click', () => buyUpgrade(button.dataset.buy, false)));
  }
  function render() {
    $('ore-count').textContent = precise(state.ore);
    $('ore-rate').textContent = precise(passiveRate());
    $('click-power').innerHTML = `${format(tapPower())} <small>ORE</small>`;
    $('core-count').textContent = format(state.cores);
    $('total-mined').textContent = format(state.totalMined);
    $('tap-yield').textContent = `+${format(tapPower())} ORE`;
    $('run-number').textContent = String(state.prestigeCount + 1).padStart(2, '0');
    $('depth-label').textContent = String(1 + Math.floor(state.totalMined / 1000)).padStart(2, '0');
    $('upgrade-owned').textContent = upgrades.filter(item => (state.levels[item.id] || 0) > 0).length;
    renderUpgrades();
    const nextIndex = milestones.findIndex((milestone, index) => !state.claimed.includes(index));
    if (nextIndex === -1) {
      $('milestone-title').textContent = 'Obelisk awakened';
      $('milestone-copy').textContent = 'All known milestones complete';
      $('milestone-reward').textContent = 'ALL CLAIMED';
      $('milestone-progress').style.width = '100%';
      $('milestone-percent').textContent = '100%';
    } else {
      const milestone = milestones[nextIndex];
      const previous = nextIndex === 0 ? 0 : milestones[nextIndex - 1].target;
      const progress = Math.max(0, Math.min(100, ((state.totalMined - previous) / (milestone.target - previous)) * 100));
      $('milestone-title').textContent = milestone.title;
      $('milestone-copy').textContent = milestone.copy;
      $('milestone-reward').textContent = `+${milestone.reward} CORE${milestone.reward > 1 ? 'S' : ''}`;
      $('milestone-progress').style.width = `${progress}%`;
      $('milestone-percent').textContent = `${Math.floor(progress)}%`;
    }
    $('bot-toggle').setAttribute('aria-pressed', String(state.bot));
    $('bot-toggle').innerHTML = `<span class="toggle-knob"></span><span>${state.bot ? 'DISENGAGE' : 'ENGAGE BOT'}</span>`;
    $('bot-strategy').value = state.strategy;
    $('bot-status').textContent = state.bot ? 'ACTIVE · ANALYZING NEXT MOVE' : 'STANDBY · AWAITING COMMAND';
    $('bot-description').textContent = state.bot ? 'Autopilot is mining and investing in your operation.' : 'Let your mining protocol make the next best move.';
    $('bot-dot').style.background = state.bot ? '#75e0aa' : '#768398';
    document.querySelector('.bot-panel').classList.toggle('active', state.bot);
    const resetGoal = nextPrestigeGoal();
    const resetButton = $('reset-button');
    resetButton.innerHTML = `<span>↻</span> ${state.runMined >= resetGoal ? 'ASCEND RUN' : 'RESET RUN'} <small>${state.runMined >= resetGoal ? `GAIN +${prestigeReward()} CORES` : `ASCEND AT ${format(resetGoal)} RUN ORE`}</small>`;
  }
  function buyUpgrade(id, automated) {
    const item = upgrades.find(upgrade => upgrade.id === id);
    if (!item) return false;
    const price = cost(item);
    if (state.ore < price) return false;
    state.ore -= price;
    state.levels[id] = (state.levels[id] || 0) + 1;
    log(`${automated ? 'Autopilot' : 'Purchased'} ${item.name} · level ${state.levels[id]}`, automated);
    render();
    return true;
  }
  function bestAffordableUpgrade() {
    const choices = upgrades.filter(item => state.ore >= cost(item));
    if (!choices.length) return null;
    const score = item => {
      const gain = item.rate * (1 + state.cores * .05) + item.tap * tapPower() * .28;
      const efficiency = gain / cost(item);
      if (state.strategy === 'rate') return (item.rate * (1 + state.cores * .05) + item.tap * .06) / cost(item);
      if (state.strategy === 'click') return (item.tap * tapPower() + item.rate * .04) / cost(item);
      return efficiency;
    };
    return choices.sort((a, b) => score(b) - score(a))[0];
  }
  function mine(manual = true) {
    const amount = tapPower() * (1 + state.cores * .05);
    addOre(amount);
    if (manual) {
      const scene = $('mine-scene').getBoundingClientRect();
      const float = document.createElement('span');
      float.className = 'float-text';
      float.textContent = `+${format(amount)}`;
      float.style.left = `${scene.width * (.38 + Math.random() * .24)}px`;
      float.style.top = `${scene.height * (.36 + Math.random() * .22)}px`;
      $('float-layer').appendChild(float);
      setTimeout(() => float.remove(), 950);
    }
    return amount;
  }
  function nextPrestigeGoal() { return Math.ceil(5000 * Math.pow(1.7, state.prestigeCount)); }
  function prestigeReward() { return Math.max(1, Math.floor(state.runMined / nextPrestigeGoal())); }
  function ascend() {
    const goal = nextPrestigeGoal();
    if (state.runMined < goal) {
      notify(`Ascension unlocks at ${format(goal)} ore mined this run.`);
      return;
    }
    const reward = prestigeReward();
    if (!confirm(`Ascend and gain ${reward} Nanite Core${reward > 1 ? 's' : ''}? Your ore and upgrades will reset; milestones and cores stay.`)) return;
    state.cores += reward;
    state.prestigeCount += 1;
    state.ore = 0;
    state.runMined = 0;
    state.levels = { servo: 0, drone: 0, laser: 0, quantum: 0 };
    state.bot = false;
    log(`Run ascended · +${reward} nanite core${reward > 1 ? 's' : ''}`, true);
    notify('New run initiated. Core bonus is permanent.');
    save(); render();
  }
  function tick(now) {
    const elapsed = Math.min(1, Math.max(0, (now - lastFrame) / 1000));
    lastFrame = now;
    const production = passiveRate() * elapsed;
    if (production > 0) addOre(production);
    if (state.bot && now - lastBotAction > 900) {
      lastBotAction = now;
      const choice = bestAffordableUpgrade();
      if (choice) buyUpgrade(choice.id, true);
      else mine(false);
    }
    if (now - lastRender > 500) { render(); lastRender = now; }
    requestAnimationFrame(tick);
  }
  $('mine-button').addEventListener('click', () => mine(true));
  $('mine-action').addEventListener('click', () => mine(true));
  $('mine-button').addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); mine(true); } });
  $('bot-toggle').addEventListener('click', () => {
    state.bot = !state.bot;
    if (state.bot) log('Progression protocol engaged · strategy: ' + state.strategy, true);
    else log('Autopilot disengaged · manual control restored', true);
    render(); save();
  });
  $('bot-strategy').addEventListener('change', event => {
    state.strategy = event.target.value;
    log(`Strategy updated · ${event.target.selectedOptions[0].text}`, true);
    if (state.bot) notify(`Autopilot strategy: ${event.target.selectedOptions[0].text}`);
    save();
  });
  $('reset-button').addEventListener('click', ascend);
  $('sound-toggle').addEventListener('click', () => notify('Sound effects are currently off.'));
  if (state._away) {
    log(`Welcome back · ${format(state._away)} ore collected while away`, true);
    notify(`${format(state._away)} Stellar Ore collected while away`);
    delete state._away;
  } else log('Mining systems online · awaiting input');
  checkMilestones();
  render();
  save();
  setInterval(save, 5000);
  requestAnimationFrame(tick);
})();
