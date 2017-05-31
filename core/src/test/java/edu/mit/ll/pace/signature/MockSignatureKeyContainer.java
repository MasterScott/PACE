/*
 * Copyright 2016 MIT Lincoln Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.mit.ll.pace.signature;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Mock {@link SignatureKeyContainer} to use for testing the signature code.
 */
public class MockSignatureKeyContainer implements SignatureKeyContainer {

  /**
   * Class for key lookup in key container map.
   */
  private static final class KeyLookup {
    private final ValueSigner signer;
    private final int keyLength;
    private final String id;

    KeyLookup(ValueSigner signer, int keyLength, String id) {
      this.signer = signer;
      this.keyLength = keyLength;
      this.id = id;
    }

    @Override
    public int hashCode() {
      return Objects.hash(signer, keyLength, id);
    }

    @Override
    public boolean equals(Object obj) {
      if (null == obj || !(obj instanceof KeyLookup)) {
        return false;
      }

      KeyLookup other = (KeyLookup) obj;
      return signer.equals(other.signer) && keyLength == other.keyLength && id.equals(other.id);
    }
  }

  /**
   * Set of keys generated by this class.
   */
  private static final Map<KeyLookup,KeyPair> generatedKeys = new HashMap<>();

  /**
   * The signing key id.
   */
  private final String signingKeyId;

  /**
   * The signing key wrapped by this container.
   */
  private final PrivateKey signingKey;

  /**
   * The public key wrapped by this container.
   */
  private final Map<String,PublicKey> verifyingKeys = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  /**
   * Create a mock container with the given signature and verifying keys.
   *
   * @param signer
   *          Signer to generate keys for.
   * @param keyLength
   *          Length of the keys.
   * @param signingKeyId
   *          Id of the user the signing key is for.
   * @param verifyingKeyIds
   *          Id of the users that we will verify signatures for.
   */
  private MockSignatureKeyContainer(ValueSigner signer, int keyLength, String signingKeyId, String... verifyingKeyIds) throws NoSuchAlgorithmException {
    this.signingKeyId = String.format("%s_%d_%s", signer.getKeyGenerationAlgorithm(), keyLength, signingKeyId);
    this.signingKey = getKeyPair(signer, keyLength, signingKeyId).getPrivate();

    for (String keyId : verifyingKeyIds) {
      this.verifyingKeys.put(String.format("%s_%d_%s", signer.getKeyGenerationAlgorithm(), keyLength, keyId), getKeyPair(signer, keyLength, keyId).getPublic());
    }
  }

  @Override
  public PrivateKeyWithId getSigningKey() {
    return new PrivateKeyWithId(signingKey, signingKeyId.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public PublicKey getVerifyingKey(byte[] id) {
    String keyId = new String(id, StandardCharsets.UTF_8);
    return verifyingKeys.get(keyId);
  }

  /**
   * Get a key pair of given length for the given ID.
   *
   * @param signer
   *          Signer that the key is being generated for.
   * @param keyLength
   *          Length of the key.
   * @param id
   *          Id of the user the key is for.
   * @return KeyPair for the requested key.
   */
  private static KeyPair getKeyPair(ValueSigner signer, int keyLength, String id) throws NoSuchAlgorithmException {
    KeyLookup mapKey = new KeyLookup(signer, keyLength, id);
    if (!generatedKeys.containsKey(mapKey)) {
      KeyPairGenerator keyGen = KeyPairGenerator.getInstance(signer.getKeyGenerationAlgorithm());
      keyGen.initialize(keyLength);
      generatedKeys.put(mapKey, keyGen.generateKeyPair());
    }

    return generatedKeys.get(mapKey);
  }

  /**
   * Get the key length for a given signer.
   *
   * @param signer
   *          The singer to get the length for.
   * @return The length for generating keys.
   */
  private static int getKeyGenLength(ValueSigner signer) {
    switch (signer) {
      case RSA_PKCS1:
      case RSA_PSS:
      case DSA:
        return 1024;

      case ECDSA:
        return 256;

      default:
        throw new UnsupportedOperationException();
    }
  }

  /**
   * Create mock containers for each value signer for the given signing key id and verifying key id.
   *
   * @param signingKeyId
   *          Id of the user the signing key is for.
   * @param verifyingKeyIds
   *          Id of the users that we will verify signatures for.
   */
  static Map<ValueSigner,SignatureKeyContainer> getContainers(String signingKeyId, String... verifyingKeyIds) throws NoSuchAlgorithmException {
    Map<ValueSigner,SignatureKeyContainer> containers = new HashMap<>();
    for (ValueSigner signer : ValueSigner.values()) {
      containers.put(signer, new MockSignatureKeyContainer(signer, getKeyGenLength(signer), signingKeyId, verifyingKeyIds));
    }
    return containers;
  }
}
