export const lendingContract = `environment lending

type Customer = Customer { id: Num, name: String }
type Customer/2 = Customer { id: Num, name: String, tier: String }

customer: Customer
customer/2: Customer/2
fun creditScore(c: Customer): Num
fun creditScore/2(c: Customer/2): Num

release 1
  Customer
  customer
  creditScore

release 2
  Customer/2
  customer/2
  creditScore/2
`;
