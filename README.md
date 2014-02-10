# cas-single-sign-out

A Ring middleware for CAS single sign out, intended for use with
[`clj-cas-client`](https://github.com/olabini/clj-cas-client/).

`cas-single-sign-out` keeps track of the Ring session associated with each CAS
service ticket. When `cas-single-sign-out` receives a single sign out request
from the CAS server, it destroys the associated session.

## Installation

To install, add the following to your project `:dependencies`:

    [cas-single-sign-out "0.1.0"]

## Usage

1. Create a Ring session store explicitly and pass it to `wrap-session`.

2. Wrap the handler returned by `wrap-session` with `wrap-cas-single-sign-out`
   passing it the same session store.

```clojure
(require '[ring.middleware.session.memory :refer [memory-store]]
         '[clj-cas-client.core :refer [cas]]
         '[cas-single-sign-out.middleware :refer [wrap-cas-single-sign-out]])

(def app (let [session-store (memory-store)]
           (-> handler
             (cas cas-server service-name)
             (wrap-session {:store session-store})
             (wrap-cas-single-sign-out session-store)))
```

## License

Copyright © 2014 [Solita](http://www.solita.fi)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
