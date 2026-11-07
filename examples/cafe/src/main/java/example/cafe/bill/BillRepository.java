package example.cafe.bill;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

interface BillRepository extends ListCrudRepository<Bill, Long> {

    /** Unpaid, or paid by a payment that is not booked yet: the bills a check still looks for. */
    List<Bill> findByPaymentBookedFalseOrderById();

    List<Bill> findAllByOrderByIdDesc();
}
