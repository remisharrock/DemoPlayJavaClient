package akkaSystem;

import akka.actor.*;
import scala.concurrent.duration.FiniteDuration;
import scala.util.Random;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;


public class LookupActor extends UntypedActor {
    private final Random random = new Random();
    private String identifyId = random.nextInt() + "";
    private Hashtable<Integer, ActorRef> actorTable = new Hashtable<Integer, ActorRef>();
    private ActorRef asker;

    @Override
    public void onReceive(Object message) {

        if (message instanceof GetAllActors) {
            FiniteDuration finiteDuration = ((GetAllActors) message).getFiniteDuration();
            ActorSelection actorSelection = getContext().actorSelection("../creatorActor/**");
            identifyId = random.nextInt() + "";
            actorTable.clear();
            asker = getSender();
            getContext().system().scheduler().scheduleOnce(finiteDuration,
                    getSelf(), "timeoutMessage", getContext().dispatcher(), getSelf());
            actorSelection.tell(new Identify(identifyId), getSelf());
        } else {
            if (message instanceof ActorIdentity) {
                ActorIdentity identity = (ActorIdentity) message;
                if (identity.correlationId().equals(identifyId)) {
                    ActorRef ref = identity.getRef();
                    if (ref != null) {
                        actorTable.put(new Integer(ref.path().uid()), ref);
                    }
                }
            } else {
                if (message instanceof String && ((String) message).equals("timeoutMessage")) {

                    AllActors allActors = new AllActors();
                    for (ActorRef ar : actorTable.values()) {
                        allActors.addActor(ar);
                    }
                    asker.tell(allActors, getSelf());
                }else{
                    unhandled(message);
                }
            }
        }
    }

    public static class GetAllActors implements Serializable {

        private FiniteDuration finiteDuration;

        public GetAllActors(FiniteDuration finiteDuration) {

            this.finiteDuration = finiteDuration;
        }

        public FiniteDuration getFiniteDuration() {
            return finiteDuration;
        }
    }

    public static class AllActors implements Serializable{
        private ArrayList<ActorRef> list = new ArrayList<ActorRef>();
        public AllActors(){
        }
        public void addActor(ActorRef ar){
            list.add(ar);
        }
        public ArrayList<ActorRef> getAllActors(){
            return list;
        }
    }
}

