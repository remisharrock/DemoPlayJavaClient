package akkaSystem;

import akka.actor.ActorRef;
import akka.actor.InvalidActorNameException;
import akka.actor.Props;
import akka.actor.UntypedActor;

import java.io.Serializable;

public class CreatorActor extends UntypedActor {
  @Override
  public void onReceive(Object message) {

    if (message instanceof CreateActor) {
        String name = ((CreateActor) message).getName();

        try {
            ActorRef actorRef = getContext().actorOf(Props.create(MyActor.class), name);
            System.out.println(getSelf().toString() + " Created actor " + name);
            getSender().tell(new ActorCreated(actorRef),getSelf());
        }catch (InvalidActorNameException e){
            System.out.println(getSelf().toString() + e.message() + " for " + name);
            getSender().tell(new ActorCreationException(e), getSelf());
            //e.printStackTrace();
        }
    } else {
      unhandled(message);
    }
  }

    public static class CreateActor implements Serializable {
        public String getName() {
            return name;
        }

        private String name;
        public CreateActor(String name){
            this.name=name;
        }
    }

    public static class ActorCreated implements Serializable{
        public ActorRef getActorRef() {
            return actorRef;
        }

        private ActorRef actorRef;
        public ActorCreated(ActorRef actorRef){
            this.actorRef=actorRef;
        }
    }

    public static class ActorCreationException implements Serializable{
        private InvalidActorNameException e;

        public ActorCreationException(InvalidActorNameException e) {
            this.e = e;
        }

        public InvalidActorNameException getE() {
            return e;
        }
    }
}
