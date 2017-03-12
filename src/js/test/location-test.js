var assert = require('assert');
var loc = require('../lib/location');

describe("location handling", () => {
  it('moves loc down', () => {
    assert.deepEqual(loc.moveLoc("\n", new loc.Location()).toJS(),
                     {line: 2, col: 1});
  });
});