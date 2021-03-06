package org.fisco.bcos.sdk.abi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.fisco.bcos.sdk.abi.datatypes.Array;
import org.fisco.bcos.sdk.abi.datatypes.Bytes;
import org.fisco.bcos.sdk.abi.datatypes.BytesType;
import org.fisco.bcos.sdk.abi.datatypes.DynamicArray;
import org.fisco.bcos.sdk.abi.datatypes.StaticArray;
import org.fisco.bcos.sdk.abi.datatypes.Type;
import org.fisco.bcos.sdk.abi.datatypes.Utf8String;
import org.fisco.bcos.sdk.abi.datatypes.generated.Bytes32;
import org.fisco.bcos.sdk.utils.Numeric;
import org.fisco.bcos.sdk.utils.StringUtils;

/** Decodes values returned by function or event calls. */
public class FunctionReturnDecoder {

    private FunctionReturnDecoder() {}

    /**
     * Decode ABI encoded return values from smart contract function call.
     *
     * @param rawInput ABI encoded input
     * @param outputParameters list of return types as {@link TypeReference}
     * @return {@link List} of values returned by function, {@link Collections#emptyList()} if
     *     invalid response
     */
    public static List<Type> decode(String rawInput, List<TypeReference<Type>> outputParameters) {
        String input = Numeric.cleanHexPrefix(rawInput);

        if (StringUtils.isEmpty(input)) {
            return Collections.emptyList();
        } else {
            return build(input, outputParameters);
        }
    }

    /**
     * Decodes an indexed parameter associated with an event. Indexed parameters are individually
     * encoded, unlike non-indexed parameters which are encoded as per ABI-encoded function
     * parameters and return values.
     *
     * <p>If any of the following types are indexed, the Keccak-256 hashes of the values are
     * returned instead. These are returned as a bytes32 value.
     *
     * <ul>
     *   <li>Arrays
     *   <li>Strings
     *   <li>Bytes
     * </ul>
     *
     * <p>See the <a href="http://solidity.readthedocs.io/en/latest/contracts.html#events">Solidity
     * documentation</a> for further information.
     *
     * @param rawInput ABI encoded input
     * @param typeReference of expected result type
     * @param <T> type of TypeReference
     * @return the decode value
     */
    @SuppressWarnings("unchecked")
    public static <T extends Type> Type decodeIndexedValue(
            String rawInput, TypeReference<T> typeReference) {
        String input = Numeric.cleanHexPrefix(rawInput);

        try {
            Class<T> type = typeReference.getClassType();

            if (Bytes.class.isAssignableFrom(type)) {
                return TypeDecoder.decodeBytes(input, (Class<Bytes>) Class.forName(type.getName()));
            } else if (Array.class.isAssignableFrom(type)
                    || BytesType.class.isAssignableFrom(type)
                    || Utf8String.class.isAssignableFrom(type)) {
                return TypeDecoder.decodeBytes(input, Bytes32.class);
            } else {
                return TypeDecoder.decode(input, 0, type);
            }
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException("Invalid class reference provided", e);
        }
    }

    private static List<Type> build(String input, List<TypeReference<Type>> outputParameters) {
        List<Type> results = new ArrayList<>(outputParameters.size());

        int offset = 0;
        for (TypeReference<?> typeReference : outputParameters) {
            try {
                @SuppressWarnings("unchecked")
                Class<Type> cls = (Class<Type>) typeReference.getClassType();

                int hexStringDataOffset = getDataOffset(input, offset, typeReference.getType());

                Type result;
                if (DynamicArray.class.isAssignableFrom(cls)) {
                    result =
                            TypeDecoder.decodeDynamicArray(
                                    input, hexStringDataOffset, typeReference.getType());
                } else if (StaticArray.class.isAssignableFrom(cls)) {
                    int length =
                            Integer.parseInt(
                                    cls.getSimpleName()
                                            .substring(StaticArray.class.getSimpleName().length()));
                    result =
                            TypeDecoder.decodeStaticArray(
                                    input, hexStringDataOffset, typeReference.getType(), length);
                } else {
                    result = TypeDecoder.decode(input, hexStringDataOffset, cls);
                }

                results.add(result);

                offset +=
                        Utils.getOffset(typeReference.getType())
                                * TypeDecoder.MAX_BYTE_LENGTH_FOR_HEX_STRING;

            } catch (ClassNotFoundException e) {
                throw new UnsupportedOperationException("Invalid class reference provided", e);
            }
        }
        return results;
    }

    private static <T extends Type> int getDataOffset(
            String input, int offset, java.lang.reflect.Type type) throws ClassNotFoundException {
        if (Utils.dynamicType(type)) {
            return TypeDecoder.decodeUintAsInt(input, offset) << 1;
        } else {
            return offset;
        }
    }
}
