const {readForms} = require('./reader');
const im = require('immutable');
const {Record, List, Map, Set} = im;
var process = require('process');
const e = require('./env');
const {Env} = e;
const a = require('./analyser');
const {compileExpr, compileNodeNS} = require('./compiler');
const vm = require('vm');
const {createHash} = require('crypto');

function EnvQueue() {
  var env = new Env({});

  var envQueue = [];
  var running = false;

  function runQueue() {
    var f = envQueue.shift();
    return f().then(_ => {
      if (envQueue.length > 0) {
        setTimeout(runQueue, 0);
      } else {
        running = false;
      }
    });
  }

  return {
    currentEnv: function() {
      return env;
    },

    updateEnv: function(f) {
      return new Promise(function (resolve, reject) {
        envQueue.push(function() {
          return f(env).then(newEnv => {
            env = newEnv;
            resolve(env);
          }).catch(reject);
        });

        if (!running) {
          running = true;
          runQueue();
        }
      });
    }
  };
};

function evalJS(js) {
  return new vm.Script(js).runInThisContext();
}

function envRequire(env, nsHeader, {hash, forms}) {
  const {nsEnv, codes} = forms.reduce(
    ({nsEnv, codes}, form) => {
      const expr = a.analyseForm(env, nsEnv, form);
      let code;
      ({nsEnv, code} = compileExpr(env, nsEnv, expr));
      return {nsEnv, codes: codes.push(code)};
    },
    {
      nsEnv: a.resolveNSHeader(env, nsHeader),
      codes: new List()
    });

  const nsCode = compileNodeNS(env, nsEnv, codes.join("\n"));
  const loadNS = evalJS(nsCode)(e, im);

  return {
    nsCode, newEnv: env.setIn(['nsEnvs', nsEnv.ns], loadNS(nsEnv))
  };
}

function envLoadJS(env, nsHeader, loadNS) {
  return {newEnv: env.setIn(['nsEnvs', nsHeader.ns], loadNS(a.resolveNSHeader(env, nsHeader)))};
}

function readNS(ns, brj) {
  const forms = readForms(brj);
  const nsHeader = a.readNSHeader(ns, forms.first());
  return {nsHeader, forms: forms.shift()};
}

function nsDependents(nsHeader) {
  return Set(nsHeader.aliases.valueSeq()).union(nsHeader.refers.valueSeq().flatten());
}

/*
  we want something that, given forms, returns a seq of namespaces with headers
  and compiledexprs - then, it's up to something else to write the file, eval
  into a namespace, etc those might all be in this file, of course, but I
  suspect that's where the split is.
*/

/*
  ah, macros in the same namespace. arse. so looks like we are going to have to
  compile a form at a time - at least in node. maybe we can do the refers for
  the namespace as a whole. or, maybe we can bring in _everything_ that _could_
  be aliased. might not do so well for the dead code elimination though.
*/

/*
  so what's the pipeline now? pretty much has to be that each form gets
  analysed, then compiled, then eval'd. can all get written out together. we
  /could/ do it lazily - only compile out whichever forms are needed for the
  macros - which wouldn't impact the vast majority of namespaces, in practice.

  so, at the moment, we can run through the NS in order - if a form refers to a
  var within the namespace which is a macro, we could compile the namespace so
  far and eval it. think this has the advantage that we can retro-fit it, too.
*/


module.exports = function(nsIO) {
  var queue = EnvQueue();

  function resolveNSsAsync(env, ns) {
    const preLoadedNSs = Set(env.nsEnvs.keySeq());

    function resolveNSAsync(ns) {
      return nsIO.resolveNSAsync(ns, 'brj').catch(e => null).then(brj => {
        if (brj == null) {
          return Promise.reject(`Can't find namespace '${ns}'`);
        } else {
          const {nsHeader, forms} = readNS(ns, brj);
          return {ns, nsHeader, forms};
        }
      });
    }

    function resolveQueueAsync({loadedNSs, nsLoadOrder, queuedNSs}) {
      if (queuedNSs.isEmpty()) {
        return nsLoadOrder.map(ns => loadedNSs.get(ns));
      } else {
        return Promise.all(queuedNSs.map(ns => resolveNSAsync(ns))).then(results => {
          queuedNSs = Set();
          results.forEach(({ns, nsHeader, hash, forms, loadNS}) => {
            const dependentNSs = nsDependents(nsHeader);
            loadedNSs = loadedNSs.set(ns, Map({nsHeader, loadNS, hash, forms}));
            nsLoadOrder = nsLoadOrder.unshift(ns);
            queuedNSs = queuedNSs.delete(ns).union(dependentNSs.subtract(queuedNSs, preLoadedNSs));
          });

          return resolveQueueAsync({loadedNSs, nsLoadOrder, queuedNSs});
        });
      }
    }

    return resolveQueueAsync({
      loadedNSs: Map(),
      nsLoadOrder: List(),
      queuedNSs: Set.of(ns)
    });
  }

  function envRequireAsync(env, ns) {
    if (env.nsEnvs.get(ns) === undefined) {
      return resolveNSsAsync(env, ns).then(
        loadedNSs => loadedNSs.reduce(
          (envAsync, loadedNS) => envAsync.then(
            env => {
              const {nsHeader, loadNS, hash, forms} = loadedNS.toObject();
              const {newEnv, nsCode} = loadNS ? envLoadJS(env, nsHeader, loadNS) : envRequire(env, nsHeader, {hash, forms});

              return newEnv;
            }),

          Promise.resolve(env)));
    } else {
      return Promise.resolve(env);
    }
  }

  const coreEnvAsync = queue.updateEnv(env => envRequireAsync(env, 'bridje.kernel'));

  function runMain(ns, argv) {
    queue.updateEnv(env => envRequireAsync(env, ns).then(
      env => {
        const mainFn = env.getIn(['nsEnvs', ns, 'exports', 'main', 'value']);
        mainFn(argv);

        // TODO we have to run an IO action if it's here, even though kernel
        // shouldn't really know about IO

        return env;
      }).catch (e => console.log(e)));
  }

  function currentEnv() {
    return queue.currentEnv();
  }

  return {envRequireAsync, envRequire, currentEnv, coreEnvAsync, runMain};
};
