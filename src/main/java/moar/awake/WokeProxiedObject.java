package moar.awake;

/**
 * Interface where we can obtain a proxy private.
 */
interface WokeProxiedObject {
  WokePrivateProxy privateProxy();
}
