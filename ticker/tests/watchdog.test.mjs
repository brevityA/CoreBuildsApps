import test from 'node:test';
import assert from 'node:assert/strict';

test('startWatchdog calls onStall when offset stalls', async () => {
  const { startWatchdog } = await import('../public/js/watchdog.js');

  let stallCount = 0;
  const fakeTicker = { offset: 100, speed: 80, measure() {} };

  const stop = startWatchdog(fakeTicker, {
    interval: 20,
    onStall() { stallCount++; },
  });

  await new Promise((r) => setTimeout(r, 80));
  stop();
  assert.ok(stallCount >= 1, `expected at least 1 stall callback, got ${stallCount}`);
});

test('startWatchdog does not stall when offset advances', async () => {
  const { startWatchdog } = await import('../public/js/watchdog.js');

  let stallCount = 0;
  let offsetVal = 0;
  const fakeTicker = {
    get offset() { return offsetVal; },
    speed: 80,
    measure() {},
  };

  const stop = startWatchdog(fakeTicker, {
    interval: 20,
    onStall() { stallCount++; },
  });

  const advancer = setInterval(() => { offsetVal += 10; }, 10);
  await new Promise((r) => setTimeout(r, 80));
  clearInterval(advancer);
  stop();
  assert.equal(stallCount, 0);
});

test('startWatchdog does not stall when speed is 0', async () => {
  const { startWatchdog } = await import('../public/js/watchdog.js');

  let stallCount = 0;
  const fakeTicker = { offset: 50, speed: 0, measure() {} };

  const stop = startWatchdog(fakeTicker, {
    interval: 20,
    onStall() { stallCount++; },
  });

  await new Promise((r) => setTimeout(r, 80));
  stop();
  assert.equal(stallCount, 0);
});

test('stop cleans up interval', async () => {
  const { startWatchdog } = await import('../public/js/watchdog.js');

  let stallCount = 0;
  const fakeTicker = { offset: 100, speed: 80, measure() {} };

  const stop = startWatchdog(fakeTicker, {
    interval: 20,
    onStall() { stallCount++; },
  });

  stop();
  const countAtStop = stallCount;
  await new Promise((r) => setTimeout(r, 60));
  assert.equal(stallCount, countAtStop);
});
