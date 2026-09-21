/*
 * The page's one door to the outside: Supabase for the rules, static JSON for
 * the tables, the browser for a position, OpenStreetMap for an address.
 *
 * It replaces the local python server. Every call the page used to make to
 * /api/... is a function here with the same shape of answer, so the page
 * itself changed as little as possible.
 */
(function(){
  "use strict";

  var URL = "https://umumewxrzwqxbcvttmmf.supabase.co";
  // The publishable key is meant to sit in a page. On its own it reaches
  // nothing: every row is behind a policy that needs the signed-in driver's id.
  var KEY = "sb_publishable_ctg4zQ0W9kCNTH8IPg7fGg_8o25NAjr";
  var TABLE = "rules";

  var client = window.supabase.createClient(URL, KEY);

  function fetchJson(path){
    return fetch(path).then(function(r){ return r.json(); });
  }

  function user(){
    return client.auth.getUser().then(function(res){ return res.data.user || null; });
  }

  /** The driver's row, or null when there is none yet. */
  function row(){
    return client.from(TABLE).select("rules, updated_at").maybeSingle().then(function(res){
      if (res.error) throw res.error;
      return res.data;
    });
  }

  window.API = {
    signIn: function(email, password){
      return client.auth.signInWithPassword({ email: email, password: password })
        .then(function(res){ if (res.error) throw res.error; return res.data.user; });
    },
    signOut: function(){ return client.auth.signOut(); },
    user: user,

    /** Everything the page needs to draw: the tables, the rules, who is signed in. */
    state: function(){
      return Promise.all([
        fetchJson("data/suburbs.json"),
        fetchJson("data/stores.json"),
        row(),
        user(),
      ]).then(function(parts){
        var r = parts[2];
        return {
          suburbs: parts[0],
          stores: parts[1],
          rules: r ? r.rules : null,
          places: r && r.rules ? (r.rules.places || []) : [],
          stamp: r ? r.updated_at : null,
          user: parts[3] ? parts[3].email : null,
        };
      });
    },

    cbd: function(){ return fetchJson("data/cbd.json"); },

    /** Straight to OpenStreetMap; it allows pages to ask it. */
    geocode: function(query){
      var url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q="
        + encodeURIComponent(query + ", Victoria, Australia");
      return fetch(url).then(function(r){ return r.json(); }).then(function(found){
        if (!found.length) return { hit: null };
        return { hit: { lat: parseFloat(found[0].lat), lon: parseFloat(found[0].lon), name: found[0].display_name } };
      }).catch(function(error){ return { hit: null, error: String(error) }; });
    },

    /** This device's own position, which on a laptop is roughly the house. */
    location: function(){
      return new Promise(function(resolve){
        if (!navigator.geolocation) return resolve({ location: null });
        navigator.geolocation.getCurrentPosition(
          function(pos){ resolve({ location: { lat: pos.coords.latitude, lon: pos.coords.longitude } }); },
          function(){ resolve({ location: null }); },
          { timeout: 10000 }
        );
      });
    },

    /** The phone's board has not moved to the cloud yet. */
    jobs: function(){ return Promise.resolve({ jobs: [], detail: "手机上的单还没上云，暂时看不了" }); },

    /**
     * Writes the whole rules object as the driver's row. Refused when the row
     * changed after this page loaded it, so a stale tab cannot bury a newer
     * save - which is how a live set was lost once.
     */
    save: function(rules, stamp){
      return user().then(function(u){
        if (!u) throw new Error("not signed in");
        return row().then(function(current){
          if (current && stamp && current.updated_at !== stamp) {
            return { saved: false, stale: true, detail: "这个页面打开之后规则被改过了，刷新页面再改，不然会盖掉新的" };
          }
          return client.from(TABLE)
            .upsert({ user_id: u.id, rules: rules }, { onConflict: "user_id" })
            .select("updated_at").single()
            .then(function(res){
              if (res.error) throw res.error;
              return { saved: true, pushed: true, stamp: res.data.updated_at };
            });
        });
      });
    },
  };
})();
