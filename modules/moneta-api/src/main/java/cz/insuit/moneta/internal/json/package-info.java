/**
 * Not API: the library's own binding of MONETA's JSON, public only because Java has no other way to share it
 * across packages. Anything here can change in any release.
 *
 * <p>One record per object, named exactly as MONETA names the keys. Jackson binds them by those
 * names, so a key is written once, here, and the rest of the library reads typed fields. Unknown keys are
 * ignored. A key the library relies on is checked in the compact constructor, so a missing one fails at
 * binding, as {@link MonetaException.InvalidResponse}, and not as a null three calls later.
 */
@NullMarked
package cz.insuit.moneta.internal.json;

import org.jspecify.annotations.NullMarked;
