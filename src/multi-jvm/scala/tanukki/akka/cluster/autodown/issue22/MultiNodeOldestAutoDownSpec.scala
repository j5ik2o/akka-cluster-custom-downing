package tanukki.akka.cluster.autodown.issue22

import akka.cluster.{Member, MultiNodeClusterSpec}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeSpec, STMultiNodeSpec}
import akka.testkit.LongRunningTest

import scala.collection.immutable
import scala.collection.immutable.SortedSet
import scala.concurrent.duration._


class OldestAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode1 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = true))
class OldestAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode2 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = true))
class OldestAutoDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode3 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = true))


class OldestAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode1 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = false))
class OldestAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode2 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = false))
class OldestAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode3 extends MultiNodeOldestAutoDownSpec(MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = false))


abstract class MultiNodeOldestAutoDownSpec(multiNodeConfig: MultiNodeOldestAutoDownSpecConfig) extends MultiNodeSpec(multiNodeConfig)
with STMultiNodeSpec with MultiNodeClusterSpec {
  import multiNodeConfig._

  muteMarkingAsUnreachable()

  "The oldest member in a 3 node cluster" must {
    "issue-22" taggedAs LongRunningTest in {
      awaitClusterUp(nodeA, nodeB, nodeC)

      println(membersByAge)

      enterBarrier("get-node-address")
      val secondAddress = node(nodeB).address
      val thirdAddress = node(nodeC).address
      runOn(nodeA) {
        enterBarrier("exit-node-b-c")

        testConductor.exit(nodeB, 0).await
        testConductor.exit(nodeC, 0).await

        enterBarrier("down-node-b-c")
        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(secondAddress)
        markNodeAsUnavailable(thirdAddress)

        // --- HERE THE LEADER SHOULD DETECT FAILURE AND AUTO-DOWN THE UNREACHABLE NODE ---

        awaitMembersUp(numberOfMembers = 1, canNotBePartOfMemberRing = Set(secondAddress, thirdAddress), 30.seconds)
      }

      runOn(nodeB, nodeC) {
        enterBarrier("node-b-c")
      }

      runOn(nodeA) {
        enterBarrier("node-a")
        awaitMembersUp(numberOfMembers = 1, canNotBePartOfMemberRing = Set(secondAddress, thirdAddress), 30.seconds)
      }

    }
  }

  def membersByAge: SortedSet[Member] = immutable.SortedSet(clusterView.members.toSeq: _*)(Member.ageOrdering)

  def roleByMember(member: Member): RoleName = roles.find(r => address(r) == member.address).get

  def isFirst(roleName: RoleName): Boolean = address(roleName) == address(nodeA)

}

