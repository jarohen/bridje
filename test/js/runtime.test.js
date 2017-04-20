const {loadFormsAsync, compileForms, evalNodeForms} = require('../../src/js/runtime');
const {Env, NSHeader} = require('../../src/js/env');
const {readForms} = require('../../src/js/reader');
const {Map, List, Set} = require('immutable');
const {fakeNSResolver} = require('./fake-nsio');
const nsio = require('../../src/js/nsio');
const realNSIO = nsio({projectPaths: ["src/brj", "test/brj"]});

var assert = require('assert');

describe('runtime', () => {
  function requireNSAsync(env, ns, filesByExt) {
    return loadFormsAsync(env, ns, {
      resolveNSAsync: fakeNSResolver(filesByExt),
      readForms
    }).then(loadedNSs => loadedNSs.reduce((env, loadedNS) => evalNodeForms(env, compileForms(env, loadedNS)), new Env({})));
  }

  const baseEnvAsync = requireNSAsync(undefined, 'bridje.kernel', {brj: {'bridje.kernel': `(ns bridje.kernel)`}});

  it ('loads a simple kernel', () => {
    return baseEnvAsync.then(env => {

      const newEnv = evalNodeForms(env, compileForms(env, Map({
        nsHeader: NSHeader({ns:'bridje.kernel.test'}),
        forms: readForms(`(def hello ["hello world"])`)
      })));

      assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.test').exports.get('hello').value.toJS(), ['hello world']);
    });
  });

  it ('requires in another namespace', async () => {
    const filesByExt = {
      brj: {
        'bridje.kernel': `(ns bridje.kernel)`,
        'bridje.kernel.foo': `(ns bridje.kernel.foo) (def (flip x y) [y x])`,
        'bridje.kernel.bar': `(ns bridje.kernel.bar {refers {bridje.kernel.foo [flip]}}) (def hello (flip 4 3))`
      }};

    const newEnv = await requireNSAsync(await baseEnvAsync, 'bridje.kernel.bar', filesByExt);

    assert.deepEqual(newEnv.nsEnvs.get('bridje.kernel.bar').exports.get('hello').value.toJS(), [3, 4]);
  });
});
