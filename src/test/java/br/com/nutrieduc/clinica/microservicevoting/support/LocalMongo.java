package br.com.nutrieduc.clinica.microservicevoting.support;

import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;

/** Inicia um MongoDB real e temporário; a conexão nunca usa MONGODB_URI. */
public class LocalMongo implements AutoCloseable {
    private final TransitionWalker.ReachedState<RunningMongodProcess> process =
            Mongod.instance().start(Version.Main.V7_0);

    public String uri() {
        var address = process.current().getServerAddress();
        return "mongodb://" + address.getHost() + ":" + address.getPort();
    }

    @Override
    public void close() {
        process.close();
    }
}
