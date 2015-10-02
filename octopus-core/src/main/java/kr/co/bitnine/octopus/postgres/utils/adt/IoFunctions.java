package kr.co.bitnine.octopus.postgres.utils.adt;

import kr.co.bitnine.octopus.postgres.catalog.PostgresType;

import java.util.HashMap;
import java.util.Map;

public final class IoFunctions
{
    private static final Map<PostgresType, IoFunction> typeToIo = new HashMap<>();

    static
    {
        typeToIo.put(PostgresType.INT4, new IoInt4());
        typeToIo.put(PostgresType.INT8, new IoInt8());
        typeToIo.put(PostgresType.VARCHAR, new IoVarchar());
    }

    private IoFunctions() { }

    public static IoFunction ofType(PostgresType type)
    {
        return typeToIo.get(type);
    }
}
